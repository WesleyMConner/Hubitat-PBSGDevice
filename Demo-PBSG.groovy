// ---------------------------------------------------------------------------------
// P B S G 2
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------
// For reference:
//   Unicode 2190 ← LEFTWARDS ARROW
//   Unicode 2192 → RIGHTWARDS ARROW
import com.hubitat.hub.domain.Event as Event

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lUtils
#include wesmc.lPBSG

definition (
  name: 'Demo-PBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Demonstrate PushButtonSwitchGroup (PBSG) Functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(
    name: 'SolicitPbsgData',
    title: h1("TestPBSG (${app.id})"),
    install: true,
    uninstall: true
  )
}

Map SolicitPbsgData() {
  dynamicPage(name: "SolicitPbsgData") {
    // Consume pbsgNames, producing pbsgConfigs
    ArrayList pbsgNames = ['weekDays', 'dogs']
    ArrayList pbsgConfigs = []
    String pbsgName = pbsgNames.pop()
    Integer loopA = 0
    if (pbsgName) {
      loopA++
      // Loops until all config data keys for pbsgName are present.
      // Then, advance to the next pbsgName (if available) or exit.
      section {
        paragraph("loopA: ${loopA}")
        paragraph("pbsgName: ${pbsgName}")
        paragraph("pbsgNames: ${pbsgNames}")
        paragraph("pbsgConfigs: ${pbsgConfigs}")
        String buttonsKey = "${pbsgName}_ButtonsKey"
        String dfltButtonKey = "${pbsgName}_DefaultKey"
        ArrayList buttonList = settings."${buttonsKey}"?.tokenize(' ')
        Integer buttonCount = buttonList?.size()
        ArrayList settingsKeys = (settings as Map).keySet() // Set→List
        ArrayList heading = []
        switch (buttonCount) {
          case null:
            heading << h2("Create at least two buttons for PBSG ${b(pbsgName)}")
            heading << i("Enter button names ${b('delimited with spaces')}")
            break
          case 1:
            heading << h1('Oops only one button was detected')
            heading << "${b('Buttons')}: ${buttonList}, ${b('Button Count')}: ${buttonCount}"
            heading << h2("Create at least two buttons for PBSG ${b(pbsgName)}")
            break
          default:
            heading << h2("Button names for PBSG ${b(pbsgName)}")
            heading << i("button names ${b('delimited with spaces')}")
        }
        input(
          name: buttonsKey,
          title: heading.join('<br/>'),
          type: 'text',
          submitOnChange: true,
          required: true,
          multiple: false
        )
        if (buttonCount > 2 && !settingsKeys.contains(dfltButtonKey)) {
          //paragraph(h2("PBSG '${b(pbsgName)}' has buttons: ${buttonList}"))
          input(
            name: dfltButtonKey,
            title: "Default Button for ${b(pbsgName)}:",
            type: 'enum',
            submitOnChange: true,
            required: false,
            multiple: false,
            options: buttonList
          )
        } else {
          pbsgConfigs << [
            name: pbsgName,
            buttons: buttonList,
            dfltButton: settings."${dfltButtonKey}"
          ]
          pbsgName = pbsgNames.pop()
          paragraph("TBD NEXT PBSG")
          paragraph("pbsgConfigs: ${pbsgConfigs}")
          paragraph("pbsgName: ${pbsgName}")
        }
      }
    }
  }
}

    /*
    section {
      int i = 0
      Map config1 = solicitPbsgConfig(1)
      logInfo('SolicitPbsgData', "config1: ${config1}")
      Map config1 = solicitPbsgConfig(2)
      logInfo('SolicitPbsgData', "config2: ${config2}")
      Map config1 = solicitPbsgConfig(3)
      logInfo('SolicitPbsgData', "config3: ${config3}")
    }
    */

void initialize() {
  setLogLevel('INFO')
  /*
  ArrayList pbsgNames = ['weekDays']

  // Expand each config Map into a full-blown PBSG.
  Map pbsg1 = pbsg_BuildToConfig(config.name)
  pbsgNames.each{ name ->
    config_SolicitButtons("${name}ButtonsString")
    if (settings."${name}ButtonsString") {
      paragraph "#67 ${settings."${name}ButtonsString"}"
    } else {
      paragraph "#69 No data yet"
    }
  }
  */
  /*
  settings.uiPbsgConfigs.each{ config ->
    Map pbsg = pbsg_BuildToConfig(config.name)
    logWarn('initialize', "Created PBSG: ${pbsg} from solicited data")
  }
  // Create a PBSG from a config Map (without user input).
  atomicState.TestPBSG = [
    'name': 'TestPBSG',
    'instType': 'pbsg',
    'allButtons': ['one', 'two', 'three', 'four', 'five', 'six'],
    'defaultButton': 'four'
  ]
  pbsg_BuildToConfig('TestPBSG')
  logWarn('initialize', "Created PBSG: ${pbsg} from hard-coded config data")
  */
}

void installed() {
  unsubscribe()
  initialize()
}

void uninstalled() {
}

void updated() {
  unsubscribe()
  initialize()
}

void pbsg_ButtonOnCallback(Map pbsg) {
  logInfo('pbsg_ButtonOnCallback(...)', "Received button: ${pbsg.activeButton}")
}

