package i2scim.authz

import data.acis

allow {
   count(rules) > 0
}

rules[rule] {
	some x
    startswith(input.path,acis[x].path)
    hasRight(acis[x],input.operation)
    isActorMatch(acis[x])

    rule = acis[x]
}

hasRight(aci,right) {
   privs = split(aci.rights,", ")

   some i
   right == privs[i]
}

isActorMatch(aci) {
    some j
	actor = aci.actors[j]
    isTypeMatch(actor)
}

isTypeMatch(actor) {
	actor == "any"
}

isTypeMatch(actor) {
    actor == "self"
    input.path == "/Me"
}

isTypeMatch(actor) {
	# let scim evaluate
	startswith(actor,"ref")
}

isTypeMatch(actor) {
	# let scim evaluate
	startswith(actor,"filter")
}

isTypeMatch(actor) {
    startswith(actor,"role=")
	x := replace(actor,"role=","")
    vals = split(x," ")
    count(vals) > 0
    count(input.auth.roles) > 0
    some i,j
    input.auth.roles[i] == vals[j]
}
