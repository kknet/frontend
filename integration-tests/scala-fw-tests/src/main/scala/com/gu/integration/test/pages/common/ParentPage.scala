package com.gu.integration.test.pages.common

import org.openqa.selenium.WebDriver
import com.gu.automation.support.TestLogging
import com.gu.integration.test.PageHelper

/**
 * This is a parent class for all Page Objects and all it does it pull in some traits, most importantly the PageHelper, so that
 * not all concrete page objects need to do it explicitly.
 */
abstract class ParentPage(implicit driver: WebDriver) extends TestLogging with PageHelper {
}