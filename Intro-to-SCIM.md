# i2scim.io

## Introduction to SCIM

![scim](docs/SCIM_B-and-W_792x270.jpg)

SCIM protocol enables developers to support a standard way to provision, update, and manage identities
from customer and partner organizations. SCIM helps reduce costs integrating services by:
* Avoiding the connector request of the day by using an industry standard protocol
* Reduce errors and inconsistencies by allowing provisioning systems to co-ordinate identities and access between
  services
* Providing a standard API for identity management
  
## Where is SCIM Used?
In some cases, SCIM is deployed as a
directory server such as with Microsoft Azure or Oracle Identity Cloud Service (IDCS). As a directory, many
applications can access a common stateful store for User profile information. Similarly SCIM
servers often serve as the backing store for OAuth Authorization and OpenId Connect systems.

SCIM is also used (as originally intended) as a provisioning service enabling a common open API to
create and update User accounts in cloud based applications and services (e.g. SFDC).

### What is SCIM Schema?

In the JSON world "schema" can be a controversial topic. In the SCIM world because many service providers will
implement SCIM, there had to be a way to tell SCIM clients how to read and understand service endpoints with slightly
different set-ups. For example, one server may have only Users and Groups (the most common), but other servers may
have other custom resource types like Devices, Applications, etc.

In a SCIM server, resources are collected in top-level containers known as *Resource Types*. Each resource (record)
has a defined "schema" which lays out the attributes contained within the
record, their characteristics and types (e.g. boolean). In addition to a main "schema", an individual resource may be
extended with additional attributes known as an *extension schema*. This enables applications to attach local
attributes, while preserving inter-operability with the standardized attributes such as defined in the User schema.

SCIM standardizes common attributes (aka claims) about Users and
Groups [RFC7643](https://tools.ietf.org/html/rfc7643). Users, Groups, and other objects are handled as JSON
documents (aka resources). SCIM schema defines attribute types:
* String,
* Integer,
* Decimal,
* Boolean,
* Date,
* Binary,
* Reference,
* Complex, and
* Multi-valued.

For each attribute defined, there are also a set of characteristics that define how to parse and how the attribute
is used:
* What type is the attribute (see above)?
* Is the attribute required?
* Does that attribute have pre-defined canonical values?
* Is the attribute matched as case exact? (case-insensitive)
* Are values immutable?
* When is the attribute returned (never, on request, by default, etc)?
* Is the attribute unique (for example a username)?

### Core Attributes
SCIM defines the following core attributes that are present in every SCIM Resource:
* `id` - Every resource has a permanent (immutable) resource identifier. To access a resource, the URL is always of
  the form `https://<serverdns>/<ResourceType>/<id>` where:
    * `<serverdns>` is the domain name of the server
    * `<ResourceType>` is the type of resource such as `Users` or `Groups`
    * `<id>` the unique and permanent identifier for the resource.
* `schemas` - tells readers what attributes can be found in a JSON document and how to handle the attribute based on
  type and characteristics.  Schemas is multi-valued where one value is the "core" schema defining the contents of
  the main JSON document, and other values are "extension" schemas used to attach additional attributes (e.g. such
  as application data) not found in the core or standardized schemas.
* `meta` - Contains metadata about the resource such as `version`, `lastModified`, `resourceType`, and `location`.
* `externalid` - an identifier used by an external client to uniquely reference the resource (optional).

An important aspect of SCIM is that all resource URIs a permanent - not subject to change. By using a globally unique
identifier, external references (e.g. such as to Users within Groups) are not subject to link breakages. This provides SCIM services with
a natural form of referential integrity.

#### An example SCIM Resource document:
```json
{
 "schemas":
   ["urn:ietf:params:scim:schemas:core:2.0:User",
     "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"],

 "id": "2819c223-7f76-453a-413861904646",
 "externalId": "701984",

 "userName": "bjensen@example.com",
 "name": {
   "formatted": "Ms. Barbara J Jensen, III",
   "familyName": "Jensen",
   "givenName": "Barbara",
   "middleName": "Jane",
   "honorificPrefix": "Ms.",
   "honorificSuffix": "III"
 },
 "...other core attrs...": "..vals.",

 "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User": {
   "employeeNumber": "701984",
   "costCenter": "4130",
   "...other extension attrs...": "..vals."
 },

 "meta": {
   "resourceType": "User",
   "created": "2010-01-23T04:56:22Z",
   "lastModified": "2011-05-13T04:42:34Z",
   "version": "W\/\"3694e05e9dff591\"",
   "location":
     "https://example.com/v2/Users/2819c223-7f76-453a-413861904646"
 }
}
```
### SCIM API / Protocol
SCIM Protocol [RFC7644](https://tools.ietf.org/html/rfc7644) is technically just a profile of HTTP
[RFC7230-40](https://tools.ietf.org/html/rfc7230). In other words, SCIM is just a REST-ful API that
defines things like create, read/search, update and delete methods using HTTP methods.

* CREATE: Is used to create an object in SCIM, the HTTP POST method is used where the body of the request is simply a
  JSON
  document. The server generates a unique ID and returns the accepted result. For example, a create request is 
  performed using HTTP POST
 ```http request
POST /Users  HTTP/1.1
HOST: example.com
Accept: application/scim+json
Content-Type: application/scim+json
Authorization: Bearer h480djs93hd8
 
{
  "schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
  "userName":"bjensen",
  "externalId":"bjensen",
  "name":{
    "formatted":"Ms. Barbara J Jensen III",
    "familyName":"Jensen",
    "givenName":"Barbara"
  }
}
```
   The server responds with:
```http request
HTTP/1.1 201 Created
Content-Type: application/scim+json
Location: https://example.com/v2/Users/2819c223-7f76-453a-919d-413861904646
ETag: W/"e180ee84f0671b1"

{
  "schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
  "id":"2819c223-7f76-453a-919d-413861904646",
  "externalId":"bjensen",
  "meta":{
    "resourceType":"User",
    "created":"2011-08-01T21:32:44.882Z",
    "lastModified":"2011-08-01T21:32:44.882Z",
    "location": "https://example.com/v2/Users/2819c223-7f76-453a-919d-413861904646",
    "version":"W\/\"e180ee84f0671b1\""
  },
  "name":{
    "formatted":"Ms. Barbara J Jensen III",
    "familyName":"Jensen",
    "givenName":"Barbara"
  },
  "userName":"bjensen"
}
```

* GET: Is used to retrieve a specific resource or search for resources using a filter.

* PUT: Is used to replace a resource with a new representation. Note: because some attributes may be immutable, or
  read-only, SCIM defines a flexible approach making it easy for a client to simply GET a resource, make a change, and
  PUT it back.

* PATCH: Is used to modify a SCIM Resource using a format similar to JSON Patch [RFC6902](https://tools.ietf.
  org/html/rfc6902). While slightly more complex for clients than HTTP PUT, PATCH allows discrete attribute modification without having
  to transmit the whole resource. For example, modifying a Group with a 100K or millions of members becomes difficult
  to handle in Javascript, not to mention the overhead and security risk in transferring such large values.

Example SCIM Patch request to remove a User from a group...
```http request
PATCH /Groups/acbf3ae7-8463-...-9b4da3f908ce
Host: example.com
Accept: application/scim+json
Content-Type: application/scim+json
Authorization: Bearer h480djs93hd8
If-Match: W/"a330bc54f0671c9"

{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
  "Operations":[{
    "op":"remove",
    "path":"members[value eq \"2819c223-7f76-...413861904646\"]"
  }]
}
```

Example SCIM Patch to remove all members of a group...
```http request
PATCH /Groups/acbf3ae7-8463-...-9b4da3f908ce
Host: example.com
Accept: application/scim+json
Content-Type: application/scim+json
Authorization: Bearer h480djs93hd8
If-Match: W/"a330bc54f0671c9"

{ "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
  "Operations":[{
    "op":"remove","path":"members"
  }]
}
```

* DELETE: Is used to delete a resource specified by the request URL.

In addition to the basic CRUD methods above, i2scim also supports the following HTTP protocol items:

* HEAD: Like GET, HEAD allows a client to query the existence of a resource and get its most recent ETag value which
  allows a client to detect whether the resource has changed.

* Conditional Requests [RFC7232](https://tools.ietf.org/html/rfc7232) place conditions on GET, HEAD, PUT, and PATCH
  requests. For example, only update a resource if it is unchanged since a certain date, or still has a specific Etag
  version value.