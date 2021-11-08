# i2Scim Access Control using Open Policy Agent

## Introduction

Starting with release 0.6, i2scim includes preview integration support for [Open Policy Agent](https://www.openpolicyagent.org)
(known as OPA) integration. From the OPA Introduction:

> The Open Policy Agent (OPA, pronounced “oh-pa”) is an open source, general-purpose policy engine that unifies 
policy enforcement across the stack. OPA provides a high-level declarative language that lets you specify policy as code and simple APIs to offload policy decision-making from your software. You can use OPA to enforce policies in microservices, Kubernetes, CI/CD pipelines, API gateways, and more.

When integrated with i2scim, i2scim will ask the configured OPA Agent server whether a request is authorized and 
what ACI policy to apply. OPA will execute the Rego policy script [i2scim.authz](/opa/policy/i2scim-authz.rego) and return two values: 
`allow` and 
`rules`. Allow is a simple boolean that if not true, i2scim returns an unauthorized error. If `true`, the `rules` 
variable
is parsed and interpreted as a normal i2scim [access control ACI](AccessControl.md). The reason why rego must return a 
rule to i2scim, is that i2scim must still determine what attributes in a request are modifiable and/or returable per the 
policy. Rather than call OPA for each attribute, i2scim just uses the returned ACI to act on the OPA Agent decision.

### Example Request

A SCIM Client makes a request to retrieve the ServiceProviderConfig using a JWT token with root scope:
```http request
GET //localhost:8080/ServiceProviderConfig
Authorization:  Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ0ZXN0Lmkyc2NpbS5pbyIsImF1ZCI6ImF1ZC50ZXN0
```

Upon receipt of a SCIM HTTP request, i2scim parses the HTTP request and passes the following example OPA `input` to the 
configured OPA Agent URL
endpoint:
```http request
POST /v1/data/i2scim
Host: localhost:8181
Content-Type: application/json
```
```json
{
  "input" : {
    "http" : {
      "method" : "GET",
      "uri" : "/ServiceProviderConfig",
      "remoteHost" : "127.0.0.1",
      "remotePort" : 58967,
      "isSecure" : false,
      "headers" : {
        "Authorization" : "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ0ZXN0Lmkyc2NpbS5pbyIsImF1ZCI6ImF1ZC50ZXN0Lmkyc2NpbS5pbyIsInN1YiI6ImFkbWluIiwidXNlcm5hbWUiOiJhZG1pbiIsImNsaWVudGlkIjoidGVzdC5jbGllbnQuaTJzY2ltLmlvIiwic2NvcGUiOiJmdWxsIG1hbmFnZXIiLCJpYXQiOjE2MzU2MjE4MjIsImV4cCI6MTYzNTYyNTQyMiwianRpIjoiNTE0MDY4OWYtNTk3OC00N2M2LWE1ZTEtN2NmNGQwNTA1ZDIwIn0.SGS1wUKi9fxFsMldwR9lB_z6qqZIU6wXgBC667j6spn83WrOua4YvarUrWAfIbkCRVqYi98P6aAY_YPB8Rhfud4Glc56MgPt9ptR9iK_eZL_cKVJPQsdxGG54S5SjvIEnMz-StIR0fwoctjMJ37adwxktvVjLIK5C_svMBLkw7HyMQVBL3Ea9Gs_uTv0_hu7vGbxUSbUtirPsjamNXyDlDF2mEZHy-ZXpLdavqzV_xFqyASb4lT2PhrbtdWUdBmORG8HHR1O97n_4JuPX8yU4yj3y0izru7W7SY9pkWNSOnVniCxCMTLnCUepkX-S01UprchTo9jMwDnPXjg5CPHmw",
        "Content-type" : "application/scim+json",
        "Connection" : "Keep-Alive",
        "User-Agent" : "Apache-HttpClient/4.5.13 (Java/11.0.7)",
        "Host" : "localhost:58957",
        "Accept-Encoding" : "gzip,deflate"
      }
    },
    "auth" : {
      "type" : "JWT",
      "sub" : "admin",
      "iss" : "test.i2scim.io",
      "aud" : [ "aud.test.i2scim.io" ],
      "roles" : [ "manager", "root", "bearer", "full" ],
      "token" : "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ0ZXN0Lmkyc2NpbS5pbyIsImF1ZCI6ImF1ZC50ZXN0Lmkyc2NpbS5pbyIsInN1YiI6ImFkbWluIiwidXNlcm5hbWUiOiJhZG1pbiIsImNsaWVudGlkIjoidGVzdC5jbGllbnQuaTJzY2ltLmlvIiwic2NvcGUiOiJmdWxsIG1hbmFnZXIiLCJpYXQiOjE2MzU2MjE4MjIsImV4cCI6MTYzNTYyNTQyMiwianRpIjoiNTE0MDY4OWYtNTk3OC00N2M2LWE1ZTEtN2NmNGQwNTA1ZDIwIn0.SGS1wUKi9fxFsMldwR9lB_z6qqZIU6wXgBC667j6spn83WrOua4YvarUrWAfIbkCRVqYi98P6aAY_YPB8Rhfud4Glc56MgPt9ptR9iK_eZL_cKVJPQsdxGG54S5SjvIEnMz-StIR0fwoctjMJ37adwxktvVjLIK5C_svMBLkw7HyMQVBL3Ea9Gs_uTv0_hu7vGbxUSbUtirPsjamNXyDlDF2mEZHy-ZXpLdavqzV_xFqyASb4lT2PhrbtdWUdBmORG8HHR1O97n_4JuPX8yU4yj3y0izru7W7SY9pkWNSOnVniCxCMTLnCUepkX-S01UprchTo9jMwDnPXjg5CPHmw"
    },
    "path" : "/ServiceProviderConfig",
    "container" : "ServiceProviderConfig",
    "operation" : "read",
    "attrs" : [ "*" ]
  }
}
```

The OPA Agent runs through the ACIs defined in the configured `data` and returns a result which 
contains two values `allow` and `rules`:
```json
{"result":{
  "authz":{
    "allow":true,
    "rules":[
      {
        "actors":["role=admin root"],
        "name":"Administrators can read, search, compare all records and operational attributes",
        "path":"/",
        "rights":"read, search",
        "targetAttrs":"*"
      }
    ]
  }}
}
```
Note: the value of `rules` are i2scim ACIs to be applied for the current request. i2Scim uses the ACIs to know what 
`targetAttrs` are allowed for update or return depending on the request.

While it may seem pointless to return an i2scim ACI to the server, the benefit of externalizing the i2scim 
policy decision is that the authorization process can be customized to support enhanced or externally managed 
policy. 

## Integration Details

### OpenPolicyAgent Deployment
From the perspective of OpenPolicyAgent, i2scim is a REST API. OPA can be deployed as a "sidecar" (meaning running 
in the same container) with an i2scim server. See "[Integrating OPA](https://www.openpolicyagent.org/docs/latest/integration/)" for more information on OPA and this type of 
integration pattern.

### i2scim Configuration Properties
To enable OPA Policy Agent Integration in an i2scim deployment, set the following system configuraiton properties:
* `scim.security.enable=true` - Must be enabled.
* `scim.security.mode="OPA"` - Will cause the OPASecurityFilter plugin to activate and ScimSecurityFilter to deactivate.
* `scim.opa.authz.url="http://localhost:8181/v1/data/i2scim"` - Tells i2scim to call the OPA agent at the URL provided.
  i2scim will provide request input and will look for a boolean allow and a JSON results called rules which contains 
  one or more [i2scim ACIs](AccessControl.md).

### OPA Input Provided by i2scim

When OPA is called, i2scim constructs a JSON document with the following attributes:
* `http` - A JSON object containing HTTP request components including: method, uri, remotePort, isSecure, headers.
* `auth` - A JSON object containing the parsed authorization. 4 types are supported:  NONE, BASIC, JWT, other.
  * type `NONE` indicates an Anonymous request (no authorization header)
  * type `BASIC` indicates Basic Authorization was validated (using the encoded username and password). In Basic, the 
    following sub-attributes are provided:
    * `user` - The username provided
    * `roles` - Any roles matched for the username provided (e.g. from the local scim server)
  * type `JWT` indicates a JWT was validated. The following sub-attributes are provided
    * `sub` - The subject of the JWT token
    * `iss` - The entity that issued the token
    * `aud` - A multivalued list of audience values (who the token is to be accepted by)
    * `roles` - The roles discovered in the token including "bearer" which indicates the authentication mode
    * `token` - The raw JWT token which can be parsed by OPA (e.g. to extract other claims)
* `path` - The original request path submitted to the server
* `container` - If provided, the top-level container (e.g. Users, Groups)
* `id` - The rosource identifier if provided.
* `operation` - The SCIM Operation right (see AccessControl) requested which will be one of: `add`, `modify` (which 
  may be PATCH or 
  PUT), `delete`, `read`, `search`.
* `attrs` - The list of attributes requested where `*` is a wildcard.
* `attrsExcluded` - The list of attributes the client would not like returned.
* `filter` - The SCIM filter provided on the request.

Note at this time, i2scim integration with OPA does not expose the HTTP body of SCI Create, PUT, or PATCH 
operations as input to OPA. 

### i2scim.authz Rego

A sample i2scim policy rego file is provided which i2scim calls for authorization decisions. As discussed above, the 
code returns two JSON attributes `allow` and `rules`. `allow` is set to true if at least one rule is matched for the 
authorized request.

```go
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
```
In the Rego code, the above code block iterates throught the acis provided from the data import. If "startsWith" for 
path, hasRight, and isActorMatch all return true, the aci is added to the set of returned rules.

While this logic just duplicates the processing that i2scim normally does, it is important to note, that by 
externalizing the logic, policy can be extended and centralized as part of an overall managment system.

Below, are Rego "functions" which each check for the appropriate matching condition. Note that there are 
actually 5 isTypeMatch functions. When multiple functions of the same name exist, Rego processes this as a logical 
"or". In this case a match is achieve if one of the 5 types specified matches.

```go
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
```

### Data.json and i2Scim Access Controls

A sample [data.json](/opa/policy/data.json) file is provided which is just an i2scim acis.json file renamed to 
data.json.  This becomes the ruleset that will be processed by rego when i2scim calls the OPA agent.

```json lines
{
  "acis": [
    {
      "path" : "/",
      "name" : "Administrators can read, search, compare all records and operational attributes",
      "targetAttrs" : "*",
      "rights" : "read, search",
      "actors" : [
        "role=admin root"
      ]
    },
    {
      "path" : "/",
      "name" : "Admins can update all resources",
      "targetAttrs" : "*",
      "rights" : "add, modify, delete",
      "actors" : [
        "role=admin root"
      ]
    },
    {
      "path" : "/ServiceProviderConfig",
      "name" : "Allow unauthenticated access to ServiceProviderConfig",
      "targetAttrs" :"*",
      "rights" : "read, search",
      "actors" : ["any"]
    },
    {
      "path" : "/ResourceTypes",
      "name" : "Allow unauthenticated access to ResourceTypes",
      "targetAttrs" :"*",
      "rights" : "read, search",
      "actors" : ["any"]
    },
    {
      "path" : "/Schemas",
      "name" : "Allow unauthenticated access to Schemas",
      "targetAttrs" :"*",
      "rights" : "read, search",
      "actors" : ["any"]
    },
    
     . . . and so on . . .
    
  ]
}
```

