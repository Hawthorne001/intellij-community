// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.dev

import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.productLayout.discovery.PRODUCT_REGISTRY_PATH
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfigurationRegistry
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path

/**
 * Custom path for product properties
 */
private const val CUSTOM_PRODUCT_PROPERTIES_PATH = "idea.product.properties.path"

fun buildProductInProcess(request: BuildRequest): DevBuildResult {
  request.tracer?.let {
    TraceManager.setTracer(it)
  }
  return TraceManager.spanBuilder("build ide").setAttribute("request", request.toString()).use {
    val buildOptionsTemplate = BuildOptions()
    val configuration = createConfiguration(homePath = request.projectDir)
    val productConfiguration = getProductConfiguration(configuration, request.platformPrefix, request.baseIdePlatformPrefixForFrontend)
    buildProductFromProject(request = request, productConfiguration = productConfiguration, buildOptionsTemplate = buildOptionsTemplate)
  }
}

private fun createConfiguration(homePath: Path): ProductConfigurationRegistry {
  val projectPropertiesPath = getProductPropertiesPath(homePath)
  return Json.decodeFromString(Files.readString(projectPropertiesPath))
}

internal fun getProductPropertiesPath(homePath: Path): Path {
  // handle a custom product properties path
  return System.getProperty(CUSTOM_PRODUCT_PROPERTIES_PATH)?.let { homePath.resolve(it) }?.takeIf { Files.exists(it) }
         ?: homePath.resolve(PRODUCT_REGISTRY_PATH)
}

private fun getProductConfiguration(configuration: ProductConfigurationRegistry, platformPrefix: String, baseIdePlatformPrefixForFrontend: String?): ProductConfiguration {
  val key = if (baseIdePlatformPrefixForFrontend == null) platformPrefix else "$baseIdePlatformPrefixForFrontend$platformPrefix"
  return configuration.products.get(key)
         ?: throw ConfigurationException("No production configuration for `$key`; please add to `${PRODUCT_REGISTRY_PATH}` if needed")
}

internal class ConfigurationException(message: String) : RuntimeException(message)
