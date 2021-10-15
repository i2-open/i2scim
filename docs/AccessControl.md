# i2scim Access Control

## Introduction

i2scim uses a JSON configuration file to specify access control policy in the form of access control
instructions or ACIs. The ACI format used is inspired by LDAP access control models but has been altered to support HTTP
and SCIM. See [LDAP Access Control Model for LDAPv3](https://datatracker.ietf.org/doc/html/draft-ietf-ldapext-acl-model). 
The model has been adapted to JSON format and removes the "compare" right which does not translate to HTTP. 

Example JSON ACIs:

```json
[
  {
    "path": "/Users",
    "name": "Users can read self except userType",
    "targetAttrs": "*,-userType,-ims",
    "rights": "read,search",
    "actors": [
      "self"
    ]
  },
  {
    "path": "/Users",
    "name": "Allow default access to names and email addresses of Users",
    "targetFilter": "meta.resourceType eq User",
    "targetAttrs": "username,displayName,emails,name,phoneNumbers",
    "rights": "read",
    "actors": [
      "any"
    ]
  }
]
```

An ACI is an Access Control Instruction expressed as a JSON object that has the following attributes:

* `path` - The server URL path where the ACI is applied. "/" is global, whereas "/Users" applies to the Users endpoint (
  container). An ACI may also be applied to an individual resource (e.g. /users/resource-identifier).
* `targetFilter` - A SCIM filter specifying a set of resources (applied against the path)
* `targetAttrs` - A comma separated String containing the attributes associated with the ACI. Note: * 
  indicates the rule applies to all default attributes, and "-" excludes an attribute. Note that sub-attributes of 
  complex attributes are not fully supported in ACI instructions at this time.
* `name` - Name is a descriptive label for an ACI such as describing the ACI's purpose.
* `rights` - The permissions granted or allowed:
    * `all` - all of the rights below
    * `add` - The ability to create a resource using HTTP POST
    * `modify` - The ability to modify a resource using HTTP PUT or PATCH
    * `delete` - The ability to delete a resource using HTTP DELETE
    * `read` - The ability to return attributes from any operation.
    * `search` - The ability to use a search filter using HTTP GET or POST.
* `actors` - A multi-valued attribute whose values are the entities the rule applies to. An actor need only match one
  value condition. Valid values are:
    * `any` - any subject (including anonymous if enabled)
    * `self` - means the authenticated subject and the resource acted upon are the same (e.g. using the /Me endpoint)
    * `role` - means the authenticated subject holds the specified role:
        * for Bearer authentication asserted in JWT `scopes` claim(see property `scim.security.authen.jwt.claim.scope`),
          or
        * for basic authentication via SCIM internal asserted by the SCIM `roles` attribute.

          Notes: By default, all internal users are assigned the role `user`. Bearer authenticated clients are assigned
          the role "bearer". A subject or user matching configuration property `scim.security.root.username` is assigned
          the role of `root`.
    * `ref` - means the subject matches the reference (internal or external URI)
    * `filter` - means the authenticated subject matches the supplied SCIM filter (and is an internal user). For 
      federated clients, use the role qualifier.

###### Notes on rights:

* `search` entitles the client to apply a filter against certain attribute and return an `id`. In all cases, the
  attributes returned are determined by read entitlement. For example, a search right may allow a client to test an 
  attribute condition using filter (ie. ask a question), but does not by itself entitle the client to see the raw 
  attributes/claims.
* For `create`, `put`, or `patch`, the `read` right controls what attributes may be returned. For example, if an
  actor has `add` and/or `put` rights but not `read` right, then only common attributes: `id` and `schemas` 
  attributes are returned in the response confirming the operation success when SCIM returns a JSON result.

### ACI Evaluation Processing:

1. When multiple ACIs apply to a SCIM entity, acis are applied in order of longest path first. The operation proceeds on
   the first aci that permits the operation. The attributes permitted in the operation are the merged `targetAttrs` list
   from all matched ACIs.
2. An `aci` specified at a certain path applies to the path and any potential child nodes. For example `/` applies
   to the entire server whereas `/Users` applies to all user resources, and `/Users/<id>` applies to a specific resource.
3. If `targetfilter` and `targetattrs` specified, both must be satisfied
4. For `read`, `create`, `modify` (PUT or PATCH), `targetAttr` clauses may silently limit what may be changed or 
   added with the `targetattrs` parameter. In such cases, the unauthorized attributes are ignored and the operation may 
   proceed assuming the result matches resource schema restrictions (e.g. has values for required attributes). Note 
   that for PATCH requests, a specific `modify` will return 'Unauthorized' if the modified 
   attribute is not allowed.
5. `targetFilter` processing:  For a SCIM Create, targetFilter is applied to the new resource to be created. For all
   other operations, the filter is applied to the existing resource before the operation. When no objects match the
   targetFilter, a NotFound error is thrown.
6. When a SCIM `filter` parameter is specified and no `search` right is allowed for the path, the server will 
   return 'Unauthorized' or HTTP Status 404.

The server reads ACI configuration from a JSON file specified by the system property `scim.security.acis.path`. The
default location is `schema/acis.json`.

### Example acis.json:

```json
{
  "acis": [
    {
      "path": "/Users",
      "name": "Self and employee access to read information",
      "targetAttrs": "*,-password",
      "rights": "read, search, compare",
      "actors": [
        "self",
        "filter=employeeNumber pr"
      ]
    },
    {
      "path": "/",
      "name": "Administrators can read, search, compare all records",
      "targetAttrs": "*",
      "rights": "read, search, compare",
      "actors": [
        "filter=groups eq TeamLeaderGroup",
        "role=admin"
      ]
    },
    {
      "name": "Allow unauthenticated access to names and email addresses of Users",
      "targetFilter": "meta.resourceType eq User",
      "targetAttrs": "username,displayName,emails,name,phoneNumbers",
      "rights": "read, search, compare",
      "actors": [
        "any"
      ]
    }
  ]
}
```

###### Notes performence (using filters and groups):
To use a group defined in the local SCIM server, use a filter with the `groups` attribute associated with the 
subjects local User resource. In general, for improved performance populate 
externally authorized entities (e.g. OpenID Connect, OAuth) with roles calculated by an Identity Provider. In 
general having an Identity Provider calculate group memberships once at authorization time is an order of magnitude 
faster than calculating group membership on a per API access basis.