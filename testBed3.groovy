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
import java.time.Instant
import java.time.Duration

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
      paragraph h1('ISO8601 Date Tests')
      Instant tOut1 = java.time.Instant.now()
      Instant tIn1 = Instant.parse('2024-07-05T17:37:50.371291Z')
      Long durationMs1 = Duration.between(tIn1, tOut1).toMillis();
      paragraph "in: ${tIn1}, out: ${tOut1}, duration: ${durationMs1} ms"
      paragraph "duration: ${durationMs1/1000} s"

      Instant tIn2 = java.time.Instant.now()
      pauseExecution(150)
      Instant tOut2 = java.time.Instant.now()
      Long durationMs2 = Duration.between(tIn2, tOut2).toMillis();
      paragraph "in: ${tIn2}, out: ${tOut2}, duration: ${durationMs2} ms"
      paragraph "duration: ${durationMs2/1000} s"

      paragraph h1('Click Done to begin Thread Test')
      paragraph i('Review test output in Hubitat logs.')
    }
  }
}

/*
def consumer(Map parms = [ref: 'DEFAULT']) {
  logInfo('consumer', "before take, ref: ${parms.ref}")
  Map rxcmd = q.take()
  logInfo('consumer', "after take, rxcmd: ${rxcmd}")
}
*/

/*
def producer(Map parms = [ref: 'DEFAULT']) {
  logInfo('producer', "before put, ref: ${parms.ref}")
  Map txcmd = [a: 'one', b: 'two', c: 'three']
  q.put(txcmd)
  logInfo('installed', 'after put')
}
*/

void producer(Map parms) {
  logInfo('producer', "parms: ${bMap(parms)}")
  ArrayList log = ['']
  // logInfo('producer',
  //   "${parms.producer} has range: ${parms.range} (${getObjectClassName(parms.range)})"
  // )
  ArrayList cmds = parms.range.collect { e ->
    [name: parms.name, value: "${e}", ref: "${java.time.Instant.now()}"]
  }
  logInfo('producer', "cmds: ${cmds}")
  cmds.each{ command ->
    pauseExecution(parms.pause)
    q.put(command)
    log << command
  }
  logInfo('producer', log)
}

void consumer(Map parms) {
  logInfo('consumer', "parms: ${bMap(parms)}")
  ArrayList log = ['']
  ArrayList range = 1..30  // Tactically, limit looping to 75
  range.each { e ->
    logInfo('consumer', "At #${e}")
    Map cmd = q.take()
    Instant tOut = java.time.Instant.now()
    Instant tIn = Instant.parse(cmd.ref)
    Long qDuration = Duration.between(tIn, tOut).toMillis();
    log << [ queued: qDuration, *:cmd ]
  }
  logInfo('consumer', log)
}
void installed() {
  logInfo('installed', 'Creating queue ...')
  q = new SynchronousQueue<Map>()
  logInfo('installed', 'Queue created.')
  runInMillis(1000, 'consumer', [data: [ref: "Single Consumer"]])
  logInfo('installed', 'Consumer thread requested.')
  Map args1 = [ producer: 'A', range: 1..30, name: 'alpha', pause: 150 ]
  runInMillis(1000, 'producer', [data: args1])
  logInfo('installed', 'Producer thread requested')
}
