package de.kp.spark.arules.source
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

import de.kp.spark.arules.io.ElasticReader

class ElasticSource(@transient sc:SparkContext) {
 
  def connect(params:Map[String,Any]):RDD[Map[String,String]] = {
    /*
     * Elasticsearch is used as a data source as well as a data sink;
     * this implies that the respective indexes and mappings have to
     * be distinguished
     */
    val index = params("source.index").asInstanceOf[String]
    val mapping = params("source.type").asInstanceOf[String]
    
    val query = params("query").asInstanceOf[String]

    new ElasticReader(sc,index,mapping,query).read

  }

}