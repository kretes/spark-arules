package de.kp.spark.arules.api
/* Copyright (c) 2014 Dr. Krusche & Partner PartG
* 
* This file is part of the Spark-ARULES project
* (https://github.com/skrusche63/spark-arules).
* 
* Spark-ARULES is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* Spark-ARULES is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* Spark-ARULES. 
* 
* If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.Date

import org.apache.spark.SparkContext

import akka.actor.{ActorRef,ActorSystem,Props}
import akka.pattern.ask

import akka.util.Timeout

import spray.http.StatusCodes._

import spray.routing.{Directives,HttpService,RequestContext,Route}

import scala.concurrent.{ExecutionContext}
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

import scala.util.parsing.json._

import de.kp.spark.core.model._
import de.kp.spark.core.rest.RestService

import de.kp.spark.arules.Configuration
import de.kp.spark.arules.actor.{RuleMaster}
import de.kp.spark.arules.model._

class RestApi(host:String,port:Int,system:ActorSystem,@transient val sc:SparkContext) extends HttpService with Directives {

  implicit val ec:ExecutionContext = system.dispatcher  
  import de.kp.spark.core.rest.RestJsonSupport._
  
  override def actorRefFactory:ActorSystem = system
  
  val (duration,retries,time) = Configuration.actor   
  val master = system.actorOf(Props(new RuleMaster(sc)), name="association-master")

  private val service = "association"
 
  def start() {
    RestService.start(routes,system,host,port)
  }

  private def routes:Route = {

    path("admin" / Segment) {subject => 
	  post {
	    respondWithStatus(OK) {
	      ctx => doAdmin(ctx,subject)
	    }
	  }
    }  ~ 
    path("get" / Segment) {subject => 
	  post {
	    respondWithStatus(OK) {
	      ctx => doGet(ctx,subject)
	    }
	  }
    }  ~ 
    path("index" / Segment) {subject =>  
	  post {
	    respondWithStatus(OK) {
	      ctx => doIndex(ctx,subject)
	    }
	  }
    }  ~ 
    path("register") { 
	  post {
	    respondWithStatus(OK) {
	      ctx => doRegister(ctx)
	    }
	  }
    }  ~ 
    path("track") {
	  post {
	    respondWithStatus(OK) {
	      ctx => doTrack(ctx)
	    }
	  }
    }  ~  
    path("train") {
	  post {
	    respondWithStatus(OK) {
	      ctx => doTrain(ctx)
	    }
	  }
    }  ~ 
    pathPrefix("web") {
      /*
       * 'web' is the prefix for static public content that is
       * served from a web browser and provides a minimalistic
       * web UI for this prediction server
       */
      implicit val actorContext = actorRefFactory
      get {
	    respondWithStatus(OK) {
	      getFromResourceDirectory("public")
	    }
      }
    }
  }

  private def doAdmin[T](ctx:RequestContext,subject:String) = {
    
    subject match {
      
      case "fields" => doRequest(ctx,service,subject)
      case "status" => doRequest(ctx,service,subject)
      
      case _ => {}
      
    }
    
  }

  private def doGet[T](ctx:RequestContext,subject:String) = {
	    
	subject match {

	  /*
	   * 'antecedent' retrieves those association rules where an externally
	   * provided itemset matches the antecedent part of the association rules
	   */
	  case "antecedent" => doRequest(ctx,service,"get:antecedent")
	  /*
	   * 'consequent' retrieves those association rules where an externally
	   * provided itemset matches the consequent part of the association rules
	   */
	  case "consequent" => doRequest(ctx,service,"get:consequent")
      /*
       * 'transaction' retrieves those association rules where the discovered
       * antecedent part (within the rules) matches items of the last
       * transaction
       */
	  case "transaction" => doRequest(ctx,service,"get:transaction")
	  /*
	   * 'rule' retrieves the discovered association rules without any data 
	   * aggregation or transformation
	   */
	  case "rule" => doRequest(ctx,service,"get:rule")
	      
	  case _ => {}

	}
    
  }

  private def doIndex[T](ctx:RequestContext,subject:String) = {
    
    subject match {
      
      case "item" => doRequest(ctx,service,"index:item")
      
      case "rule" => doRequest(ctx,service,"index:rule")
      
      case _ => {}
      
    }
    
  }
  
  private def doRegister[T](ctx:RequestContext) = doRequest(ctx,service,"register")
  
  private def doTrack[T](ctx:RequestContext) = doRequest(ctx,service,"track:item")

  private def doTrain[T](ctx:RequestContext) = doRequest(ctx,service,"train")
  
  private def doRequest[T](ctx:RequestContext,service:String,task:String) = {
     
    val request = new ServiceRequest(service,task,getRequest(ctx))
    implicit val timeout:Timeout = DurationInt(time).second
    
    val response = ask(master,request).mapTo[ServiceResponse] 
    ctx.complete(response)
    
  }

  private def getHeaders(ctx:RequestContext):Map[String,String] = {
    
    val httpRequest = ctx.request
    
    /* HTTP header to Map[String,String] */
    val httpHeaders = httpRequest.headers
    
    Map() ++ httpHeaders.map(
      header => (header.name,header.value)
    )
    
  }
 
  private def getBodyAsMap(ctx:RequestContext):Map[String,String] = {
   
    val httpRequest = ctx.request
    val httpEntity  = httpRequest.entity    

    val body = JSON.parseFull(httpEntity.data.asString) match {
      case Some(map) => map
      case None => Map.empty[String,String]
    }
      
    body.asInstanceOf[Map[String,String]]
    
  }
  
  private def getRequest(ctx:RequestContext):Map[String,String] = {

    val headers = getHeaders(ctx)
    val body = getBodyAsMap(ctx)
    
    headers ++ body
    
  }

}