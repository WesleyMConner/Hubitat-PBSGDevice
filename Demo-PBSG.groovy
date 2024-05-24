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
    uninstall: true,
    parms: [a: 'apple', b: 'banana', c: 'grape']
  )
}

Map SolicitPbsgData(parms) {
  logInfo('SolicitPbsgData', "parms: ${parms}")
  dynamicPage(name: "SolicitPbsgData") {
    ArrayList pbsgNames = ['weekDays']
    section {
      pbsgNames.each{ name ->
        String buttonsStringKey = "${name}_ButtonsString"
        input(
          name: buttonsStringKey,
          title: '<b>PBSG BUTTONS</b> (space delimited)',
          width: 4,
          type: 'text',
          submitOnChange: true,
          required: true,
          multiple: false
        )
        String buttons = settings."${buttonsStringKey}"
        paragraph "#61 buttons: ${buttons}"
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

