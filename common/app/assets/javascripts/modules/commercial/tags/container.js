/**
 * A regionalised container for all the commercial tags.
 */
define([
    'common/modules/commercial/tags/audience-science',
    'common/modules/commercial/tags/audience-science-gateway',
    'common/modules/commercial/tags/imrworldwide',
    'common/modules/commercial/tags/media-math',
    'common/modules/commercial/tags/criteo',
    'common/modules/commercial/tags/remarketing',
    'common/modules/commercial/tags/amaa',
    'common/modules/commercial/tags/effective-measure'
], function(
    audienceScience,
    audienceScienceGateway,
    imrWorldwide,
    mediaMath,
    criteo,
    remarketing,
    amaa,
    effectiveMeasure
) {

    function init(config) {

        switch (config.page.edition.toLowerCase()) {
            case 'au':
                amaa.load();
                effectiveMeasure.load();
                break;

            default:
                audienceScience.load();
                audienceScienceGateway.load();
                break;
        }

        mediaMath.load();
        criteo.load();
        imrWorldwide.load();
        remarketing.load();
    }

    return {
        init: init
    };

});