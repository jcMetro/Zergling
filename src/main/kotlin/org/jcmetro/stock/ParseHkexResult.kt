package org.jcmetro.stock

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    Succeeded,
    Failed
}

fun main(args: Array<String>) {

    val startTimeMillis = System.currentTimeMillis()

    val driver = initializeChromeDriver()

    val stockCodes = filterDividendStocksFromHkex(driver)

    println(stockCodes.joinToString(separator = ","))

    val failedStockCodes = mutableSetOf<String>()
    stockCodes.forEach { stockCode ->
        println("Loading stock code $stockCode...")
        var downloadStatus = downloadMorningStarData(driver, stockCode)
        var retryCount = 0
        while (downloadStatus == DownloadStatus.Failed && retryCount < 3) {
            downloadStatus = downloadMorningStarData(driver, stockCode)
            retryCount++
        }

        if (downloadStatus == DownloadStatus.Failed){
            failedStockCodes += stockCode
        }
    }

    driver.close()

    val runDuration = System.currentTimeMillis() - startTimeMillis

    println("Run duration is: " + runDuration / 1000.0 + " seconds")
    println("Failed stock codes are: $failedStockCodes")
}

private fun filterDividendStocksFromHkex(driver: ChromeDriver): List<String> {
    driver.get("file:///Users/johnnychow/Downloads/Equities.htm")

    val stockCodes = driver.findElementsByCssSelector("tr.datarow td.code").map {
        it.text.padStart(5, '0')
    }
    return stockCodes
}

private fun initializeChromeDriver(): ChromeDriver {
    System.setProperty("webdriver.chrome.driver", "/Users/johnnychow/StockFilter/tools/chromedriver")
    val driver = ChromeDriver()
    driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS)
    return driver
}

private fun downloadMorningStarData(driver: ChromeDriver, stockCode: String): DownloadStatus {
    try {
        driver.get("http://www.morningstar.com/stocks/xhkg/$stockCode/quote.html")

        val keyRatioLink = driver.findElementByLinkText("Key Ratios")

        WebDriverWait(driver, 30000).until {
            ExpectedConditions.elementToBeClickable(keyRatioLink)
        }

        keyRatioLink.click()

        val url = driver.findElementByCssSelector(".sal-full-key-ratios a").getAttribute("href")

        driver.get(url)

        WebDriverWait(driver, 30000).until {
            driver.findElementsByTagName("a").find { it.text.contains("Export") } != null
        }

        driver.findElementsByTagName("a").find { it.text.contains("Export") }!!.click()

        Thread.sleep(1000)

        return DownloadStatus.Succeeded
    } catch (e: Exception) {
        println("Failed to retrieve record for stock code: $stockCode")
        e.printStackTrace()
        return DownloadStatus.Failed
    }
}