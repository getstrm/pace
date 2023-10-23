//package com.getstrm.pace.catalogs
//
//import kotlinx.coroutines.coroutineScope
//
//import toYaml
//
///**
// * In order to run this, you need:
// * 1. A trial account to Collibra Data Intelligence Cloud (expires after 14 days)
// * 2. A running instance of Datahub in our Kubernetes cluster and Telepresence running
// * 3. A running instance of Open Data Discovery, we've decided to go for a GCP Compute VM with ODD running in Docker through docker compose
// *    We port forward it through gcloud compute ssh, as instructed in the Readme.
// */
//suspend fun main(args: Array<String>) {
//    val collibraCatalog = CollibraCatalog()
//    val datahubCatalog = DatahubCatalog()
//    val openDataDiscoveryCatalog = OpenDataDiscoveryCatalog()
//
//    listOf(collibraCatalog, datahubCatalog, openDataDiscoveryCatalog).forEach { catalog ->
//        catalog.use {
//            coroutineScope {
//                val databases = it.listDatabases()
//                databases.forEach { db ->
//                    println(db)
//                    val schemas = db.getSchemas()
//                    schemas.forEach {
//                        println(it)
//                        it.getTables().forEach { table ->
//                            println("$table")
//                            println(table.getDataPolicy()?.toYaml())
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
