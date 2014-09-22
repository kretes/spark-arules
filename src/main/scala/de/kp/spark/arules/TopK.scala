package de.kp.spark.arules
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
import org.apache.spark.SparkContext._

import org.apache.spark.rdd.RDD

import de.kp.core.arules.{TopKAlgorithm,RuleG,Vertical}
import de.kp.spark.arules.source.{FileSource}

import scala.collection.JavaConversions._

class TopK {
  
  /**
   * Build vertical representation from external data format
   * and find Top K rules from vertical database
   */
  def extractRDDRules(dataset:RDD[(Int,Array[Int])],k:Int,minconf:Double,stats:Boolean=true):List[RuleG] = {
          
    val vertical = VerticalBuilder.build(dataset)    
    findRDDRules(vertical,k,minconf,stats)
    
  }

  /**
   * Run algorithm from vertical database and create Top K association rules
   */
  def findRDDRules(vertical:Vertical,k:Int,minconf:Double,stats:Boolean=true):List[RuleG] = {

    val algo = new TopKAlgorithm()
	val rules = algo.runAlgorithm(k, minconf, vertical)
	
	if (stats) algo.printStats()
    
    rules.toList
    
  }
  
}

object TopK {
  
  def extractFileRules(sc:SparkContext,input:String,k:Int,minconf:Double,stats:Boolean=true):List[RuleG] = {
    
    /* Retrieve data from the file system */
    val source = new FileSource(sc)
    val dataset = source.connect(input)
    
    new TopK().extractRDDRules(dataset,k,minconf,stats)
    
  }
  
  def extractRules(dataset:RDD[(Int,Array[Int])],k:Int,minconf:Double,stats:Boolean=true):List[RuleG] = {
    
    new TopK().extractRDDRules(dataset,k,minconf,stats)

  }
  
  def rulesToJson(rules:List[RuleG]):String = {
    
    String.format("""{"rules":[%s]}""", rules.map(rule => {
			
      val antecedent = rule.getItemset1().toList
      val consequent = rule.getItemset2().toList

      val support    = rule.getAbsoluteSupport()
      val confidence = rule.getConfidence()
	
      new Rule(antecedent,consequent,support,confidence).toJSON
	
    }).mkString(","))

  }
  
}