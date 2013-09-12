/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.util

import org.jetbrains.jps.LayoutInfo
import org.jetbrains.jps.gant.JpsGantProjectBuilder
//import org.jetbrains.jps.gant.JpsGantTool
//import static org.jetbrains.jps.idea.IdeaProjectLoader.guessHome


class Paths {
  final projectHome
  final buildDir
  final sandbox
  final classesTarget
  def distWin
  final distWinZip
  def distAll
  final distJars
  def distUnix
  def distMac
  final distDev
  final artifacts
  final artifacts_core_upsource
  final ideaSystem
  final ideaConfig
  def jdkHome

  def Paths(String home, String productCode) {
    // move IDEA specific field to release script
    projectHome = new File(home).getCanonicalPath()
    buildDir = "$projectHome/build"
    sandbox = "$projectHome/out/$productCode"
//    sandbox = "$projectHome/out/release"

    classesTarget = "$sandbox/classes"
    distAll = "${sandbox}/layout"
    distWin = "${sandbox}/win"
    distMac = "${sandbox}/mac"
    distUnix = "${sandbox}/unix"
    distJars = "$sandbox/dist.jars"
    distWinZip = "$sandbox/dist.win.zip"
    distDev = "$sandbox/dist.dev"
    artifacts = "$sandbox/artifacts"

    artifacts_core_upsource = "$artifacts/core-upsource"

    ideaSystem = "$sandbox/system"
    ideaConfig = "$sandbox/config"
  }
}

class Steps {
  def clear = true
  def zipSources = true
  def compile = true
  def scramble = true
  def layout = true
  def build_searchable_options = true
  def zipwin = true
  def targz = true
  def dmg = true
  def sit = true
}

class Build {
  def buildName
  def product
  def modules
  def steps
  def paths
  def home
  def projectBuilder
  def buildNumber
  def system_selector
  def teamcity_buildType_id
  def ant = new AntBuilder()
  def ch
  def usedJars
  def suffix
  Map layout_args
  Script utils
  Script ultimate_utils
  Script layouts
  Script community_layouts
  Script libLicenses

  Build(String arg_home, JpsGantProjectBuilder prjBuilder, String arg_productCode){
    home = arg_home
    projectBuilder = prjBuilder
    paths = new Paths(home, arg_productCode)
    steps = new Steps()
  }

  def init () {
    projectBuilder.stage("Cleaning, creating folders, initializing timestamps")
    utils.loadProject()

    if (steps.clear) {
      projectBuilder.stage("Cleaning up sandbox folder")
      utils.forceDelete(paths.sandbox)
      [paths.sandbox, paths.classesTarget, paths.distWin, paths.distWinZip, paths.distAll,
       paths.distJars, paths.distUnix, paths.distMac, paths.distDev, paths.artifacts].each {
        ant.mkdir(dir: it)
      }
    }
    ultimate_utils = utils.includeFile(home + "/build/scripts/ultimate_utils.gant")
    layouts = utils.includeFile(home + "/build/scripts/layouts.gant")
    community_layouts = utils.includeFile(home + "/community/build/scripts/layouts.gant")
    libLicenses = utils.includeFile(home + "/community/build/scripts/libLicenses.gant")
//    resources()
  }

  def zip() {
    if (steps.zipSources) {
      projectBuilder.stage("zip: $home $paths.artifacts")
      utils.zipSources(home, paths.artifacts)
    }
  }

  def compile(Map args) {
    paths.jdkHome = args.jdk
    projectBuilder.stage("- Compilation -")
    if (steps.compile) {
      projectBuilder.arrangeModuleCyclesOutputs = true
      projectBuilder.targetFolder = paths.classesTarget
      projectBuilder.cleanOutput()
      if (modules == null ){
        projectBuilder.buildProduction()
      }
      else{
        usedJars = ultimate_utils.buildModules(modules, args.module_libs)
      }
      projectBuilder.stage("- additionalCompilation -")
      utils.additionalCompilation()
    }
  }

  def layout(){
    projectBuilder.stage("- layout -")
    if (steps.layout) {
      LayoutInfo layoutInfo = layouts.layoutFull(paths.distJars)
      ultimate_utils.layoutUpdater(paths.artifacts)
      ultimate_utils.layoutInternalUtilities(paths.artifacts)
      layouts.layout_duplicates(paths.artifacts, "duplicates.jar")
      layouts.layout_core_upsource(home, paths.artifacts_core_upsource)
      utils.notifyArtifactBuilt(paths.artifacts_core_upsource)
      libLicenses.generateLicensesTable("${paths.artifacts}/third-party-libraries.txt", layoutInfo.usedModules)
      def jpsArtifactsPath = "$paths.artifacts/jps"
      ant.mkdir(dir: jpsArtifactsPath)
      layouts.layoutJps(home, jpsArtifactsPath)
      utils.notifyArtifactBuilt(jpsArtifactsPath)
    }
  }

  def scramble (Map args) {
    projectBuilder.stage("- scramble -")
    if (utils.isUnderTeamCity()) {
      projectBuilder.stage("Scrambling - getPreviousLogs")
      getPreviousLogs()
      projectBuilder.stage("Scrambling - prevBuildLog")
      def prevBuildLog = "$paths.sandbox/prevBuild/logs/ChangeLog.txt"
      if (!new File(prevBuildLog).exists()) prevBuildLog = null
      def inc = prevBuildLog != null ? "looseChangeLogFileIn=\"${prevBuildLog}\"" : ""
      utils.copyAndPatchFile("$home/build/conf/script.zkm.stub", "$paths.sandbox/script.zkm",
                       ["CLASSES": "\"${args.jarPath}/${args.jarName}\"", "SCRAMBLED_CLASSES": args.jarPath, "INCREMENTAL": inc])

      ant.mkdir(dir: "${paths.artifacts}/${args.jarName}.unscrambled")
      def unscrambledPath = "${paths.artifacts}/${args.jarName}.unscramble"
      ant.copy(file: "${args.jarPath}/${args.jarName}", todir: unscrambledPath)
      utils.notifyArtifactBuilt("${unscrambledPath}/${args.jar}")

      ultimate_utils.zkmScramble("${paths.sandbox}/script.zkm", args.jarPath, args.jarName)
      ant.zip(destfile: "${paths.artifacts}/logs.zip") {
        fileset(file: "ChangeLog.txt")
        fileset(file: "ZKM_log.txt")
        fileset(file: "${paths.sandbox}/script.zkm")
      }
      ant.delete(file: "ChangeLog.txt")
      ant.delete(file: "ZKM_log.txt")
    }
    else {
      projectBuilder.info("teamcity.buildType.id is not defined. Incremental scrambling is disabled")
    }
    projectBuilder.stage("- Scrambling - finished -")
  }

  private lastPinnedBuild() {
    "http://buildserver/httpAuth/repository/download/" + teamcity_buildType_id + "/.lastPinned"
    //[vo] uncomment ans test
    //"http://buildserver/httpAuth/repository/download/${this."teamcity.buildType.id"}/.lastPinned"
  }

  private getPreviousLogs() {
    def removeZip = "${lastPinnedBuild()}/logs.zip"
    def localZip = "${paths.sandbox}/prevBuild/logs.zip"
    ant.mkdir(dir: "${paths.sandbox}/prevBuild")
    ant.get(src: removeZip,
            dest: localZip,
            username: "builduser",
            password: "qpcv4623nmdu",
            ignoreerrors: "true"
    )
    if (new File(localZip).exists()) {
      ant.unzip(src: localZip, dest: "${paths.sandbox}.prevBuild/logs"){
        patternset {
          include(name: "ChangeLog.txt")
        }
      }
    }
  }

  def install() {
    projectBuilder.stage("- layoutShared -")
    layouts.layoutShared(layout_args, paths.distAll)

    projectBuilder.stage("- layoutWin -")
    layouts.layoutWin(layout_args, paths.distWin)

    projectBuilder.stage("- layoutUnix -")
    layouts.layoutUnix(layout_args, paths.distUnix)

    projectBuilder.stage("- layoutMac -")
    layouts.layoutMac(layout_args, paths.distMac)

    projectBuilder.stage("- buildNSISs -")
    buildWinInstallation()

    projectBuilder.stage("- targz -")
    utils.buildTeamServer()

    projectBuilder.stage("- checkLibLicenses -")
    libLicenses.checkLibLicenses();
  }

  // should be optimized to allow using for all IDEA based builds
  def buildWinInstallation(){
    ultimate_utils.buildNSIS([paths.distAll, paths.distJars, paths.distWin],
                             "$home/build/conf/nsis/strings.nsi", "$home/build/conf/nsis/paths.nsi",
                             "${product}-")
  }
}