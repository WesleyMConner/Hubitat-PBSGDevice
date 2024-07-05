//// Imports in support of @Field and ConcurrentHashMap<K, V>
import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap

@Field static ConcurrentHashMap<Long, Integer> perPbsgRqstCntr = [:]
@Field static ConcurrentHashMap<Long, Map> perPbsgState = [:]

void addCommandToQueue(Map command) {
  ArrayList q = csm().commandQueue ?: []
  q << command
  csm().commandQueue = q
}

void inspectCCS(fn, tag) {
  String s = csm().collect {
    k, v -> "csm().${b(k)}: ${v}"
  }.join('<br/>')
  logTrace('inspectCCS', "${fn} ${tag}<br/>${s}")
}

Map pbsg_CoreKeysOnly(Map pbsg) {
  ArrayList coreKeys = ['active', 'lifo']
  Map corePbsg = pbsg.inject([:]) { m, k, v ->
    if (coreKeys.contains(k)) { m."${k}" = v }
    return m
  }
  //-> logTrace('pbsg_CoreKeysOnly', "returning: ${bMap(corePbsg)}")
  return corePbsg
}
