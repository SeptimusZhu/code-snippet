private def jobInfoWithBackPressureOld(json: String, appId: String, jobId: String): String = {
    implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
    try {
      val transformEach = __.json.update(__.read[JsObject].map { o =>
        (o \ "status").as[String] match {
          case "RUNNING" =>
            val bpResult = SyncHttpClient.request(
              s"${yarnFramework.proxyUrlPrefix}/$appId/jobs/$jobId/vertices/${(o \ "id").as[String]}/backpressure")
            val bpContent = Json.parse(bpResult)
            val bpState = (bpContent \ "status").as[String]
            val result = bpState match {
              case "ok" =>
                o ++ Json.obj("backpressure-level" -> (bpContent \ "backpressure-level").as[String])
              case _ =>
                o
            }
            val metrics = SyncHttpClient.request(
              s"${yarnFramework.proxyUrlPrefix}/$appId/jobs/$jobId/vertices/${(o \ "id").as[String]}/metrics")
            val param = (Json.parse(metrics) \\ "id")
              .map(_.as[String])
              .filter(_.endsWith(".latency"))
              .map(_.replaceFirst("[0-9]+\\.", "0.")).toSet.mkString(",")
            val latency = (Json.parse(SyncHttpClient.request(
              s"${yarnFramework.proxyUrlPrefix}/$appId/jobs/$jobId/vertices/${(o \ "id").as[String]}/metrics?get=$param"
            )) \\ "value")
              .map(_.as[String])
              .filter(_.contains("mean="))
              .map { m =>
                val temp = m.substring(m.lastIndexOf("mean=") + 5)
                temp.substring(0, temp.indexOf(".")).toInt
              }.sum
            result ++ Json.obj("latency" -> latency)
          case _ =>
            o
        }
      })
      val transformAll = (__ \ 'vertices).json.update(
        __.read[JsArray].map(_.value.par.map(s => s.transform(transformEach)).foldLeft(JsArray())((ja, jb) => ja ++ Json.arr(jb.get)))
      )
      Json.parse(json).transform(transformAll).get.toString
    } catch {
      case e: Throwable =>
        logger.error("error when get job back pressure info", e)
        json
    }
  }

  private def jobInfoWithBackPressure(json: String, appId: String, jobId: String): String = {
    try {
      val metrics = (Json.parse(SyncHttpClient.request(
        s"${yarnFramework.proxyUrlPrefix}/$appId/jobs/$jobId/metrics")) \\ "id")
        .map(_.as[String].replaceAll("index\\.[0-9]+", "index.0")).toSet
      val transformEach = __.json.update(__.read[JsObject].map { o =>
        (o \ "status").as[String] match {
          case "RUNNING" =>
            val verticeId = (o \ "id").as[String]
            val bpResult = SyncHttpClient.request(
              s"${yarnFramework.proxyUrlPrefix}/$appId/jobs/$jobId/vertices/$verticeId/backpressure")
            val bpContent = Json.parse(bpResult)
            val bpState = (bpContent \ "status").as[String]
            val result = bpState match {
              case "ok" =>
                o ++ Json.obj("backpressure-level" -> (bpContent \ "backpressure-level").as[String])
              case _ =>
                o
            }
            val param = metrics.filter(m => m.contains(verticeId) && m.endsWith(".latency_mean")).mkString(",")
            val resp = SyncHttpClient.request(s"${yarnFramework.proxyUrlPrefix}/$appId/jobs/$jobId/metrics?get=$param")
            val latency = (Json.parse(resp) \\ "value").map(_.as[String].toFloat.toInt).sum
            result ++ Json.obj("latency" -> latency)
          case _ =>
            o
        }
      })
      val transformAll = (__ \ 'vertices).json.update(
        __.read[JsArray].map(_.value.par.map(s => s.transform(transformEach)).foldLeft(JsArray())((ja, jb) => ja ++ Json.arr(jb.get)))
      )
      Json.parse(json).transform(transformAll).get.toString
    } catch {
      case e: Throwable =>
        logger.error("error when get job back pressure info", e)
        json
    }
  }