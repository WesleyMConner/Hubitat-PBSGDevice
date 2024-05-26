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
    name: 'demoPbsg'
  )
}

ArrayList getSolicitedPbsgNames() {
  return ['weekDays', 'dogs']
}

Map demoPbsg() {
  // Solicit 'settings' for each PBSG Name from the client.
  return dynamicPage(
    name: 'demoPbsg',
    title: h1("TestPBSG (${app.id})"),
    install: true,
    uninstall: true
  ) {
    section {
      getSolicitedPbsgNames().each{ pbsgName ->
        solicitPbsgConfig(pbsgName)
      }
    }
  }
}

void installed() {
  unsubscribe()
  initialize()
}

void updated() {
  unsubscribe()
  initialize()
}

void uninstalled() {
}

void initialize() {
  setLogLevel('INFO')
  // For each pbsgName:
  //   - Process the user-provided 'settings' into a PBSG configuration Map.
  //   - The PBSG configuration Map is preserved as a top-level Map in state
  //   - The PBSG configuration Map is subsequently expanded to become a
  //     a PBSG instance.
  //   - Top-level Maps in state are efficiently processed with the
  //     atomicState() and atomicState.updateMapValue(...).
  getSolicitedPbsgNames().each { pbsgName ->
    logInfo('initialize #83', atomicState."${pbsgName}"?.toString())
    convertSolicitedPbsgConfigToState(pbsgName)
    logInfo('initialize #85', atomicState."${pbsgName}"?.toString())
    pbsg_BuildToConfig(pbsgName)
    logInfo('initialize #87', atomicState."${pbsgName}"?.toString())
  }
}

/*
void pbsg_ButtonOnCallback(String pbsgName) {
  // Once a PBSG instance is created, this method is called to communicate
  // a change in the state of the named PBSG instance.
  logInfo('pbsg_ButtonOnCallback', "pbsg: ${pbsg_State(pbsgName)}")
}
*/
