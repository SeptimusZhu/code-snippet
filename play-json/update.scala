private def jobInfoWithBackPressure(json: String, appId: String, id: String): String = {
    try {
      val transformEach = __.json.update(__.read[JsObject].map { o =>
        (o \ "status").as[String] match {
          case "RUNNING" =>
            val bpResult = SyncHttpClient.request(
              s"${yarnFramework.proxyUrlPrefix}/$appId/jobs/$id/vertices/${(o \ "id").as[String]}/backpressure")
            val bpContent = Json.parse(bpResult)
            val bpState = (bpContent \ "status").as[String]
            val result = bpState match {
              case "ok" =>
                o ++ Json.obj("backpressure-level" -> (bpContent \ "backpressure-level").as[String])
              case _ =>
                o
            }
//            val metrics = SyncHttpClient.request(
//              s"${yarnFramework.proxyUrlPrefix}/$appId/jobs/$id/vertices/${(o \ "id").as[String]}/metrics")
//            (Json.parse(metrics) \\ "id").as[]
            result
          case _ =>
            o
        }
      })
      val transformAll = (__ \ 'vertices).json.update(
        __.read[JsArray].map(_.value.map(s => s.transform(transformEach)).foldLeft(JsArray())((ja, jb) => ja ++ Json.arr(jb.get)))
      )
      Json.parse(json).transform(transformAll).get.toString
    } catch {
      case e: Throwable =>
        logger.error("error when get job back pressure info", e)
        json
    }
  }