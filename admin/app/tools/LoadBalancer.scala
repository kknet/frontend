package tools

import common.AkkaAgent
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import scala.collection.JavaConversions._
import services.AwsEndpoints

case class LoadBalancer(id: String,
                        name: String,
                        project: String,
                        url: Option[String] = None,
                        testPath: Option[String] = None)

object LoadBalancer {

  import conf.Configuration.aws.credentials

    private val loadBalancers = Seq(
      LoadBalancer("frontend-RouterLo-1HHMP4C9L33QJ", "Router", "frontend-router"),
      LoadBalancer(
        "frontend-ArticleL-T0BUR121RZIG", "Article", "frontend-article",
        testPath = Some("/uk-news/2014/jan/21/drax-protesters-convictions-quashed-police-spy-mark-kennedy")
      ),
      LoadBalancer("frontend-FaciaLoa-I92TZ7OEAX7W", "Front", "frontend-facia", testPath = Some("/uk")),
      LoadBalancer("frontend-Applicat-V36EHVHAEI15", "Applications", "frontend-applications", testPath = Some("/books")),
      LoadBalancer("frontend-Discussi-KC65SADEVHIE", "Discussion", "frontend-discussion"),
      LoadBalancer("frontend-Identity-1ITBJ706CLQIC", "Identity", "frontend-identity"),
      LoadBalancer("frontend-ImageLoa-Y3FM3W6ZRJC1", "Image", "frontend-image"),
      LoadBalancer("frontend-SportLoa-GLJK02HUD48W", "Sport", "frontend-sport"),
      LoadBalancer("frontend-Commerci-12ZQ79RIOLIYE", "Commercial", "frontend-commercial"),
      LoadBalancer("frontend-OnwardLo-14YIUHL6HIW63", "Onward", "frontend-onward"),
      LoadBalancer("frontend-R2Footba-9BHU0R3R3DHV", "R2 Football", "frontend-r2football"),
      LoadBalancer("frontend-Diagnost-1SCNCG3BR1RFE", "Diagnostics", "frontend-diagnostics" ),
      LoadBalancer("frontend-ArchiveL-C2GJNZE0TS7", "Archive", "frontend-archive" )
    )


  private val agent =  AkkaAgent(loadBalancers)

  def refresh() {
    credentials.foreach{ credentials =>
      val client = new AmazonElasticLoadBalancingClient(credentials)
      client.setEndpoint(AwsEndpoints.elb)
      val elbs = client.describeLoadBalancers().getLoadBalancerDescriptions
      client.shutdown()
      val newLoadBalancers = loadBalancers.map{ lb =>
        lb.copy(url = elbs.find(_.getLoadBalancerName == lb.id).map(_.getDNSName))
      }
      agent.send(newLoadBalancers)
    }
  }

  def all: Seq[LoadBalancer] = agent()

  def apply(project: String): Option[LoadBalancer] = agent().find(_.project == project)
}
