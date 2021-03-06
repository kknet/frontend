package dfp

import java.net.URLDecoder

import akka.agent.Agent
import com.gu.facia.client.models.CollectionConfig
import common._
import conf.Configuration.commercial._
import conf.Configuration.environment
import model.Tag
import model.`package`.frontKeywordIds
import play.api.{Application, GlobalSettings}
import services.S3

import scala.io.Codec.UTF8

trait DfpAgent {

  protected def sponsorships: Seq[Sponsorship]
  protected def tagToSponsorsMap: Map[String, Set[String]]
  protected def advertisementFeatureSponsorships: Seq[Sponsorship]
  protected def tagToAdvertisementFeatureSponsorsMap: Map[String, Set[String]]
  protected def foundationSupported: Seq[Sponsorship]
  protected def inlineMerchandisingTargetedTags: InlineMerchandisingTagSet
  protected def pageSkinSponsorships: Seq[PageSkinSponsorship]

  private def containerSponsoredTag(config: CollectionConfig,
                                    p: String => Boolean): Option[String] = {
    val stopWords = Set("newest", "order-by", "published", "search", "tag", "use-date")

    config.apiQuery.flatMap { encodedQuery =>
      val query = URLDecoder.decode(encodedQuery, "utf-8")
      val tokens = query.split( """\?|&|=|\(|\)|\||\,""")
      val possibleKeywords = tokens filterNot stopWords.contains flatMap frontKeywordIds
      val keywords = possibleKeywords filter p
      if (keywords.size == 1) keywords.headOption else None
    }
  }

  private def isSponsoredContainer(config: CollectionConfig, p: String => Boolean): Boolean = {
    containerSponsoredTag(config, p).isDefined
  }

  private def paidForTag(available: Seq[Sponsorship], tags: Seq[Tag], section: Option[String]): Option[Tag] = {
    val keywordOrSeriesTags = tags.filter(t => t.isSeries || t.isKeyword)
    keywordOrSeriesTags find (tag => isPaidFor(available, tag.id, section))
  }

  private def isPaidFor(available: Seq[Sponsorship], tags: Seq[Tag], section: Option[String]): Boolean =
    paidForTag(available, tags, section).nonEmpty

  private def isPaidFor(available: Seq[Sponsorship], tagId: String, section: Option[String]): Boolean = {

    def sectionMatches(sponsorshipSections: Seq[String]): Boolean = {
      section.isEmpty || sponsorshipSections.isEmpty ||
        sponsorshipSections.exists(section.get.startsWith)
    }

    available exists { sponsorship =>
      sponsorship.hasTag(tagId) && sectionMatches(sponsorship.sections)
    }
  }

  def sponsoredTag(tags: Seq[Tag], sectionId: Option[String]): Option[Tag] = paidForTag(sponsorships, tags, sectionId)
  def isSponsored(tags: Seq[Tag], sectionId: Option[String]): Boolean = isPaidFor(sponsorships, tags, sectionId)
  def isSponsored(tagId: String, sectionId: Option[String]): Boolean = isPaidFor(sponsorships, tagId, sectionId)
  def isSponsored(config: CollectionConfig): Boolean = isSponsoredContainer(config, {isSponsored(_, None)})

  def hasMultipleSponsors(tags: Seq[Tag]): Boolean = {
    tags.map { tag =>
      tagToSponsorsMap.getOrElse(tag.id.split("/").last, Seq[String]())
    }.flatten.toSeq.size > 1
  }

  def hasMultipleSponsors(tagId: String): Boolean = {
    (tagToSponsorsMap contains tagId) &&
      (tagToSponsorsMap(tagId).size > 1)
  }

  def hasMultipleFeatureAdvertisers(tags: Seq[Tag]): Boolean = {
    tags.map { tag =>
      tagToAdvertisementFeatureSponsorsMap.getOrElse(tag.id.split("/").last, Seq[String]())
    }.flatten.toSeq.size > 1
  }

  def hasMultipleFeatureAdvertisers(tagId: String): Boolean = {
    (tagToAdvertisementFeatureSponsorsMap contains tagId) &&
      (tagToAdvertisementFeatureSponsorsMap(tagId).size > 1)
  }

  def advertisementFeatureTag(tags: Seq[Tag], sectionId: Option[String]): Option[Tag] = paidForTag(advertisementFeatureSponsorships, tags, sectionId)
  def isAdvertisementFeature(tags: Seq[Tag], sectionId: Option[String]): Boolean = isPaidFor(advertisementFeatureSponsorships, tags, sectionId)
  def isAdvertisementFeature(tagId: String, sectionId: Option[String]): Boolean = isPaidFor(advertisementFeatureSponsorships, tagId, sectionId)
  def isAdvertisementFeature(config: CollectionConfig): Boolean = isSponsoredContainer(config, {isAdvertisementFeature(_, None)})

  def foundationSupportedTag(tags: Seq[Tag], sectionId: Option[String]): Option[Tag] = paidForTag(foundationSupported, tags, sectionId)
  def isFoundationSupported(tags: Seq[Tag], sectionId: Option[String]): Boolean = isPaidFor(foundationSupported, tags, sectionId)
  def isFoundationSupported(tagId: String, sectionId: Option[String]): Boolean = isPaidFor(foundationSupported, tagId, sectionId)
  def isFoundationSupported(config: CollectionConfig): Boolean = isSponsoredContainer(config, {isFoundationSupported(_, None)})

  def isProd = environment.isProd

  def hasInlineMerchandise(tags: Seq[Tag]): Boolean = tags exists inlineMerchandisingTargetedTags.hasTag

  def isPageSkinned(adUnitWithoutRoot: String, edition: Edition): Boolean = {

    if (PageSkin.isValidForNextGenPageSkin(adUnitWithoutRoot)) {
      val adUnitWithRoot: String = s"$dfpAdUnitRoot/$adUnitWithoutRoot"

      def targetsAdUnitAndMatchesTheEdition(sponsorship: PageSkinSponsorship) = {
        val adUnits = sponsorship.adUnits map (_.stripSuffix("/ng"))
        adUnits.contains(adUnitWithRoot) &&
          sponsorship.editions.contains(edition) &&
          !sponsorship.isR2Only
      }

      if (isProd) {
        pageSkinSponsorships.exists { sponsorship =>
          targetsAdUnitAndMatchesTheEdition(sponsorship) && !sponsorship.targetsAdTest
        }
      } else {
        pageSkinSponsorships.exists { sponsorship =>
          targetsAdUnitAndMatchesTheEdition(sponsorship)
        }
      }
    } else {
      false
    }
  }

  def sponsorshipTag(config: CollectionConfig): Option[String] = {
    containerSponsoredTag(config, {isSponsored(_, None)}) orElse
      containerSponsoredTag(config, {isAdvertisementFeature(_, None)}) orElse
      containerSponsoredTag(config, {isFoundationSupported(_, None)})
  }

  def getSponsor(tags: Seq[Tag]): Option[String] = tags.flatMap(tag => getSponsor(tag.id)).headOption

  def getSponsor(tagId: String): Option[String] = {
    def sponsorOf(sponsorships: Seq[Sponsorship]) = sponsorships.find(_.hasTag(tagId)).flatMap(_.sponsor)
    sponsorOf(sponsorships) orElse sponsorOf(advertisementFeatureSponsorships) orElse sponsorOf(foundationSupported)
  }

  def getSponsor(config: CollectionConfig): Option[String] = {
    for {
      tagId <- sponsorshipTag(config)
      sponsor <- getSponsor(tagId)
    } yield sponsor
  }

  def sponsorshipType(config: CollectionConfig): Option[String] = {
    if (isSponsored(config)) {
      Option("sponsoredfeatures")
    } else if (isAdvertisementFeature(config)) {
      Option("advertisement-features")
    } else if (isFoundationSupported(config)) {
      Option("foundation-features")
    } else {
      None
    }
  }

  def sponsorshipTag(tags: Seq[Tag], sectionId: Option[String]): Option[Tag] = {
    sponsoredTag(tags: Seq[Tag], sectionId: Option[String])
      .orElse(advertisementFeatureTag(tags: Seq[Tag], sectionId: Option[String]))
      .orElse(foundationSupportedTag(tags: Seq[Tag], sectionId: Option[String]))
  }

}


object DfpAgent extends DfpAgent with ExecutionContexts {

  private lazy val sponsoredTagsAgent = AkkaAgent[Seq[Sponsorship]](Nil)
  private lazy val tagToSponsorsMapAgent = AkkaAgent[Map[String, Set[String]]](Map[String, Set[String]]())
  private lazy val advertisementFeatureTagsAgent = AkkaAgent[Seq[Sponsorship]](Nil)
  private lazy val tagToAdvertisementFeatureSponsorsMapAgent = AkkaAgent[Map[String, Set[String]]](Map[String, Set[String]]())
  private lazy val foundationSupportedTagsAgent = AkkaAgent[Seq[Sponsorship]](Nil)
  private lazy val inlineMerchandisingTagsAgent = AkkaAgent[InlineMerchandisingTagSet](InlineMerchandisingTagSet())
  private lazy val pageskinnedAdUnitAgent = AkkaAgent[Seq[PageSkinSponsorship]](Nil)

  protected def sponsorships: Seq[Sponsorship] = sponsoredTagsAgent get()
  protected def tagToSponsorsMap = tagToSponsorsMapAgent get()
  protected def advertisementFeatureSponsorships: Seq[Sponsorship] = advertisementFeatureTagsAgent get()
  protected def tagToAdvertisementFeatureSponsorsMap = tagToAdvertisementFeatureSponsorsMapAgent get()
  protected def foundationSupported: Seq[Sponsorship] = foundationSupportedTagsAgent get()
  protected def inlineMerchandisingTargetedTags: InlineMerchandisingTagSet = inlineMerchandisingTagsAgent get()
  protected def pageSkinSponsorships: Seq[PageSkinSponsorship] = pageskinnedAdUnitAgent get()

  def generateTagToSponsorsMap(sponsorships: Seq[Sponsorship]): Map[String, Set[String]] = {
    sponsorships.foldLeft(Map.empty[String, Set[String]]) { (soFar, sponsorship) =>
      val sponsorshipSponsors = (for {
        tag <- sponsorship.tags
        sponsor <- sponsorship.sponsor
      } yield {
        tag -> (soFar.getOrElse(tag, Set.empty[String]) + sponsor)
      }).toMap

      soFar ++ sponsorshipSponsors
    }
  }

  def refresh() {

    def stringFromS3(key: String): Option[String] = S3.get(key)(UTF8)

    def grabSponsorshipsFromStore(key: String): Seq[Sponsorship] = {
      val reportOption: Option[SponsorshipReport] = for {
        jsonString <- stringFromS3(key)
        report <- SponsorshipReportParser(jsonString)
      } yield report

      reportOption.fold(Seq[Sponsorship]())(_.sponsorships)
    }

    def grabPageSkinSponsorshipsFromStore(key: String): Seq[PageSkinSponsorship] = {
      val reportOption: Option[PageSkinSponsorshipReport] = for {
        jsonString <- stringFromS3(key)
        report <- PageSkinSponsorshipReportParser(jsonString)
      } yield report

      reportOption.fold(Seq[PageSkinSponsorship]())(_.sponsorships)
    }

    def grabInlineMerchandisingTargetedTagsFromStore(): InlineMerchandisingTagSet = {
      val maybeTagSet = for {
        jsonString <- stringFromS3(dfpInlineMerchandisingTagsDataKey)
        report <- InlineMerchandisingTargetedTagsReportParser(jsonString)
      } yield report.targetedTags
      maybeTagSet getOrElse InlineMerchandisingTagSet()
    }

    def update[T](agent: Agent[Seq[T]], freshData: Seq[T]) {
      if (freshData.nonEmpty) {
        agent send freshData
      }
    }

    def updateMap(agent: Agent[Map[String, Set[String]]], freshData: Map[String, Set[String]]) {
      if (freshData.nonEmpty) {
        agent send freshData
      }
    }

    def updateInlineMerchandisingTargetedTags(freshData: InlineMerchandisingTagSet) {
      inlineMerchandisingTagsAgent sendOff { oldData =>
        if (freshData.nonEmpty) freshData else oldData
      }
    }

    val sponsoredTags: Seq[Sponsorship] = grabSponsorshipsFromStore(dfpSponsoredTagsDataKey)
    update(sponsoredTagsAgent, sponsoredTags)
    updateMap(tagToSponsorsMapAgent, generateTagToSponsorsMap(sponsoredTags))

    val advertisementFeatures: Seq[Sponsorship] = grabSponsorshipsFromStore(dfpAdvertisementFeatureTagsDataKey)
    update(advertisementFeatureTagsAgent, advertisementFeatures)
    updateMap(tagToAdvertisementFeatureSponsorsMapAgent, generateTagToSponsorsMap(advertisementFeatures))

    val foundationSupportedTags: Seq[Sponsorship] = grabSponsorshipsFromStore(dfpFoundationSupportedTagsDataKey)
    update(foundationSupportedTagsAgent, foundationSupportedTags)

    update(pageskinnedAdUnitAgent, grabPageSkinSponsorshipsFromStore(dfpPageSkinnedAdUnitsKey))
    updateInlineMerchandisingTargetedTags(grabInlineMerchandisingTargetedTagsFromStore())
  }
}


trait DfpAgentLifecycle extends GlobalSettings {

  override def onStart(app: Application) {
    super.onStart(app)

    Jobs.deschedule("DfpDataRefreshJob")
    Jobs.scheduleEveryNMinutes("DfpDataRefreshJob", 1) {
      DfpAgent.refresh()
    }

    AkkaAsync {
      DfpAgent.refresh()
    }
  }

  override def onStop(app: Application) {
    Jobs.deschedule("DfpDataRefreshJob")
    super.onStop(app)
  }
}
