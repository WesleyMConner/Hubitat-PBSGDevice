// ---------------------------------------------------------------------------------
// T E S T B E D 3
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

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include WesMC.lUtils
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput as JsonOutput
import groovy.json.JsonSlurper as JsonSlurper
import java.lang.Math as Math
import java.lang.Object as Object

import groovy.transform.Field
import java.util.concurrent.SynchronousQueue

@Field static SynchronousQueue<Map> q = [:]

definition (
  name: 'TestBed3',
  namespace: 'WesMC',
  author: 'Wesley M. Conner',
  description: 'TestBed3',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestBed3')
}

Map TestBed3() {
  return dynamicPage(
    name: 'TestBed3',
    title: 'TestBed3',
    install: true,
    uninstall: true
  ) {
    app.updateLabel("TestBed3 (${app.id})")
    section{
      paragraph 'thread test'
      input( name: 'x',
        title: "${b('Enter value for x')}",
        type: 'text',
        required: true
      )
    }
  }
}

def consumer(Map parms = [ref: 'DEFAULT']) {
  logInfo('consumer', "before take, ref: ${parms.ref}")
  Map rxcmd = q.take()
  logInfo('consumer', "after take, rxcmd: ${rxcmd}")
}

def producer(Map parms = [ref: 'DEFAULT']) {
  logInfo('producer', "before put, ref: ${parms.ref}")
  Map txcmd = [a: 'one', b: 'two', c: 'three']
  q.put(txcmd)
  logInfo('installed', 'after put')
}

void installed() {
  logInfo('installed', 'before q creation')
  q = new SynchronousQueue<Map>()
  logInfo('installed', 'after q creation')
  runInMillis(500, 'consumer', [data: [ref: "alpha"]])
// ...

  logInfo('installed', 'after consumer runIn 1')
  runInMillis(1000, 'producer', [data: [ref: "alpha"]])
  logInfo('installed', 'after producer runIn 1')
  //pauseExecution(10000)
  //logInfo('installed', 'after 10s pause')
}
