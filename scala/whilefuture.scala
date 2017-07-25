import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

def check(func: () => Future[Int]): Future[Int] = {
    Future.unit.flatMap(_ => func()) flatMap {
        case x if x <= 10 => check(func)
        case x => Future.successful(x + 100)
    }
}