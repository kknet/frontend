package services

import org.scalatest.{DoNotDiscover, Matchers, FlatSpec}
import play.api.libs.ws.WS
import test.ConfiguredTestSuite
import scala.concurrent.duration._
import scala.concurrent.Await

@DoNotDiscover class DiscussionHealthcheckTest extends FlatSpec with Matchers with ConfiguredTestSuite {

  "Healthchecks" should "pass" in goTo("/discussion/p/37v3a"){ _ =>

    Await.result(WS.url(s"http://localhost:${port}/_healthcheck").get(), 10.seconds).status should be (200)
  }
}
