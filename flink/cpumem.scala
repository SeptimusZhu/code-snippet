private def getCpuLoadAndMemUsageOld(appId: String): (Double, Double) = {
    try {
      val cpuLoad = "Status.JVM.CPU.Load"
      val memNonHeapUsed = "Status.JVM.Memory.NonHeap.Used"
      val memHeapUsed = "Status.JVM.Memory.Heap.Used"
      val memHeapMax = "Status.JVM.Memory.Heap.Max"
      val tmInfoSeq = Json.parse(SyncHttpClient.request(s"${yarnFramework.proxyUrlPrefix}/$appId/taskmanagers")) \ "taskmanagers"
      val tmSeq = (tmInfoSeq \\ "id").map(_.as[String]) zip (tmInfoSeq \\ "cpuCores").map(_.as[Int])
      val tmMetricSeq = if (tmSeq.lengthCompare(5) < 0) {
        tmSeq.map { tm =>
          val metrics = Json.parse(SyncHttpClient.request(
            s"${yarnFramework.proxyUrlPrefix}/$appId/taskmanagers/${tm._1}" +
              s"/metrics?get=$cpuLoad,$memNonHeapUsed,$memHeapUsed,$memHeapMax"))
          (((metrics \\ "id").map(_.as[String]) zip (metrics \\ "value").map(_.as[String])).toMap, tm._2)
        }

      } else {
        implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
        Await.result(Future.traverse(tmSeq) { tm =>
          AsyncHttpClient.request(
            s"${yarnFramework.proxyUrlPrefix}/$appId/taskmanagers/${tm._1}" +
              s"/metrics?get=$cpuLoad,$memNonHeapUsed,$memHeapUsed,$memHeapMax")
            .map { res =>
              val metrics = Json.parse(res)
              (((metrics \\ "id").map(_.as[String]) zip (metrics \\ "value").map(_.as[String])).toMap, tm._2)
            }
        }, 5.seconds)
      }
      val cpu = tmMetricSeq.map { m =>
        m._1.getOrElse(cpuLoad, "0").toDouble * m._2 * m._2
      }.sum / tmMetricSeq.map(_._2).sum

      val mem = tmMetricSeq.map { m =>
        (m._1.getOrElse(memHeapUsed, "0").toLong + m._1.getOrElse(memNonHeapUsed, "0").toLong,
          m._1.getOrElse(memHeapMax, "1").toLong)
      }.reduce((a, b) => (a._1 + b._1, a._2 + b._2))

      (cpu, mem._1.toDouble / mem._2)
    } catch {
      case e: Throwable =>
        logger.error("get cpu load and mem usage failed.", e)
        (0, 0)
    }
  }

  private def getCpuLoadAndMemUsage(appId: String): (Double, Double) = {
    try {
      val tmInfoSeq = Json.parse(SyncHttpClient.request(s"${yarnFramework.proxyUrlPrefix}/$appId/taskmanagers"))
      val tmSeq = (tmInfoSeq \\ "id").map(_.as[String]) zip (tmInfoSeq \\ "hardware").map(h => (h \ "cpuCores").as[Int])
      if (tmSeq.lengthCompare(5) < 0) {
        val cpuLoad = tmSeq.map { tm =>
          (Json.parse(SyncHttpClient.request(
            s"${yarnFramework.proxyUrlPrefix}/$appId/taskmanagers/${tm._1}/metrics?get=Status.JVM.CPU.Load")
          )(0) \ "value").as[String].toDouble * tm._2
        }.sum / tmSeq.length
        val memInfo = tmSeq.map { tm =>
          val memMetric = Json.parse(SyncHttpClient.request(
            s"${yarnFramework.proxyUrlPrefix}/$appId/taskmanagers/${tm._1}")) \ "metrics"
          ((memMetric \ "heapUsed").as[Long] + (memMetric \ "nonHeapUsed").as[Long],
            (memMetric \ "heapMax").as[Long])
        }.reduce((a, b) => (a._1 + b._1, a._2 + b._2))
        (cpuLoad, memInfo._1.toDouble / memInfo._2)
      } else {
        implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
        val cpuLoad = Await.result(Future.traverse(tmSeq) { tm =>
          AsyncHttpClient.request(
            s"${yarnFramework.proxyUrlPrefix}/$appId/taskmanagers/${tm._1}/metrics?get=Status.JVM.CPU.Load")
            .map(res => (Json.parse(res)(0) \ "value").as[String].toDouble * tm._2)
        }.map(_.sum / tmSeq.length), 5.seconds)
        val memInfoSeq = Await.result(Future.traverse(tmSeq) { tm =>
          AsyncHttpClient.request(
            s"${yarnFramework.proxyUrlPrefix}/$appId/taskmanagers/${tm._1}"
          ).map { res =>
            val memMetric = Json.parse(res) \ "metrics"
            ((memMetric \ "heapUsed").as[Long] + (memMetric \ "nonHeapUsed").as[Long],
              (memMetric \ "heapMax").as[Long])
          }
        }, 5.seconds)
        val memInfo = memInfoSeq.reduce((a, b) => (a._1 + b._1, a._2 + b._2))
        (cpuLoad, memInfo._1.toDouble / memInfo._2)
      }
    } catch {
      case e: Throwable =>
        logger.error("get cpu load and mem usage failed.", e)
        (0, 0)
    }
  }