package de.kp.spark.arules.actor
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

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import de.kp.spark.arules.TopK

import de.kp.spark.arules.source.TransactionSource
import de.kp.spark.arules.model._

import de.kp.spark.arules.redis.RedisCache

class TopKActor(@transient val sc:SparkContext) extends MLActor {
 
  def receive = {

    case req:ServiceRequest => {
      
      val params = properties(req)
      val missing = (params == null)
      
      /* Send response to originator of request */
      sender ! response(req, missing)

      if (missing == false) {
        /* Register status */
        RedisCache.addStatus(req,ResponseStatus.MINING_STARTED)
 
        try {
          
          val source = new TransactionSource(sc)
          /*
           * STEP #1: 
           * Discover rules from transactional data
           */
          val dataset = source.transDS(req.data)
          val rules = if (dataset != null) findRules(req,dataset,params) else null
          /*
           * STEP #2: 
           * Merge rules with transactional data and build weighted rules
           * thereby filtering those below a dynamically provided threshold
           */
          if (rules != null) {
           
            val dataset = source.itemsetDS(req.data)               
            if (dataset != null) {

              val weight = req.data("weight").toDouble
              findMultiUserRules(req,dataset,rules,weight)
              
           }
            
          }
          
        } catch {
          case e:Exception => RedisCache.addStatus(req,ResponseStatus.FAILURE)          
        }
 

      }
      
      context.stop(self)
          
    }
    
    case _ => {
      
      log.error("Unknown request.")
      context.stop(self)
      
    }
    
  }
    
  private def findRules(req:ServiceRequest,dataset:RDD[(Int,Array[Int])],params:(Int,Double)):List[Rule] = {

    RedisCache.addStatus(req,ResponseStatus.DATASET)
          
    val (k,minconf) = params               
    val rules = TopK.extractRules(dataset,k,minconf).map(rule => {
     
      val antecedent = rule.getItemset1().toList.map(_.toInt)
      val consequent = rule.getItemset2().toList.map(_.toInt)

      val support    = rule.getAbsoluteSupport()
      val confidence = rule.getConfidence()
	
      new Rule(antecedent,consequent,support,confidence)
            
    })
          
    saveRules(req,new Rules(rules))
          
    /* Update cache */
    RedisCache.addStatus(req,ResponseStatus.RULES)
    
    rules
    
  }
  
  private def properties(req:ServiceRequest):(Int,Double) = {
      
    try {
      
      val k = req.data("k").toInt
      val minconf = req.data("minconf").toDouble
        
      return (k,minconf)
        
    } catch {
      case e:Exception => {
         return null          
      }
    }
    
  }

}