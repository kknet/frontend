define([
    'bean',
    'bonzo',
    'common/utils/$',
    'common/utils/detect',
    'common/utils/mediator',
    'common/utils/storage',
    'common/utils/template',
    'text!common/views/commercial/creatives/expandable-v2.html'
], function (
    bean,
    bonzo,
    $,
	detect,
    mediator,
    storage,
    template,
    expandableV2Tpl
) {

    /**
     * https://www.google.com/dfp/59666047#delivery/CreateCreativeTemplate/creativeTemplateId=10028247
     */
    var ExpandableV2 = function ($adSlot, params) {
        this.$adSlot      = $adSlot;
        this.params       = params;
        this.isClosed     = true;

        if (detect.isBreakpoint({min: 'tablet'})) {
            this.closedHeight = Math.min(bonzo.viewport().height / 3, 300);
            this.openedHeight = Math.min(bonzo.viewport().height * 2 / 3, 600);
        } else {
            this.closedHeight = '150';
            this.openedHeight = '300';
        }
    };

    ExpandableV2.prototype.listener = function () {
        if ((window.pageYOffset + bonzo.viewport().height) > (this.$ad.offset().top + this.openedHeight)) {
            // expires in 1 week
            var week = 1000 * 60 * 60 * 24 * 7;

            storage.local.set('gu.commercial.expandable.' + this.params.ecid, true, { expires: Date.now() + week });
            this.$button.toggleClass('button-spin');
            this.$ad.css('height', this.openedHeight);
            this.isClosed = false;

            return true;
        }
    };

    ExpandableV2.prototype.create = function () {
        var $expandablev2 = $.create(template(expandableV2Tpl, this.params));

        this.$ad     = $('.ad-exp--expand', $expandablev2).css('height', this.closedHeight);
        this.$button = $('.ad-exp__close-button', $expandablev2);

        $('.ad-exp-collapse__slide', $expandablev2).css('height', this.closedHeight);

        if (this.params.trackingPixel) {
            this.$adSlot.before('<img src="' + this.params.trackingPixel + this.params.cacheBuster + '" class="creative__tracking-pixel" height="1px" width="1px"/>');
        }

        $expandablev2.appendTo(this.$adSlot);

        if (!storage.local.get('gu.commercial.expandable.' + this.params.ecid)) {
            mediator.on('window:scroll', this.listener.bind(this));
        }

        bean.on(this.$adSlot[0], 'click', '.ad-exp__close-button', function () {
            this.$button.toggleClass('button-spin');
            this.$ad.css('height', this.isClosed ? this.openedHeight : this.closedHeight);
            this.isClosed = !this.isClosed;
        }.bind(this));
    };

    return ExpandableV2;

});
