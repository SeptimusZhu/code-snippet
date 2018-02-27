import slick.codegen.SourceCodeGenerator
import slick.jdbc.JdbcProfile

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object CodeGen extends App {


  val Array(profile, jdbcDriver, url, outputFolder, pkg, user, password) =
    Array("slick.jdbc.PostgresProfile", "org.postgresql.Driver",
        "jdbc:postgresql://129.188.16.62:5432/xxx", "app", "dao",
        "xxx", "pswd")

    val profileInstance: JdbcProfile =
    Class.forName(profile + "$").getField("MODULE$").get(null).asInstanceOf[JdbcProfile]
    val dbFactory = profileInstance.api.Database
    val db = dbFactory.forURL(url, driver = jdbcDriver,
        user = user, password = password, keepAliveConnection = true)
    try {
      val m = Await.result(db.run(profileInstance.createModel(None, true)(ExecutionContext.global).withPinnedSession), Duration.Inf)
        new SourceCodeGenerator(m) {
          //override def code: String = "import models.JobStatus.JobStatus" + "\n" + super.code
          override def Table = new Table(_) {
            override def EntityType = new EntityType {
              // TODO: revert for slick 3.2.1
              override def caseClassFinal: Boolean = false
            }
            override def Column = new Column(_) {
              override def rawType: String = model.name match {
                //case "STATUS" => "JobStatus"
                case _ => super.rawType
              }
            }
          }
        }.writeToFile(profile, outputFolder, pkg)
    } finally db.close
}
