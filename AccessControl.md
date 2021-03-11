# Access Control

## Introduction

This SCIM server uses a configuration filter access.aci that specifies access control policy for the server.

This ACI format is inspired by LDAP access control models but has been altered to support HTTP and SCIM. For example,
because SCIM has no comparable "compare" operation which returns a simple true/false, it does not supported.

Example JSON ACIs:
```
{
    "path" : "/Users",
    "name" : "Users can read self except userType",
    "targetAttrs": "*,-userType,-ims",
    "rights" : "read,search",
    "actors" : [ "self"]
},

{
  "path" : "/Users",
  "name" : "Allow default access to names and email addresses of Users",
  "targetFilter":"meta.resourceType eq User",
  "targetAttrs" :"username,displayName,emails,name,phoneNumbers",
  "rights" : "read",
  "actors" : ["any"]
}
```
An `aci` is an Access Control Instruction expressed as a JSON object that has the following attributes:
* `path` - The server path where the ACI is applied. "/" is global, whereas "/Users" applies to the Users endpoint. An
  ACI may also be applied to an individual resource (e.g. /users/resource-identifier).
* `targetFilter` - A SCIM filter specifying a set of resources
* `targetAttrs` - A comma separated String containing the attriibutes applied to the aci. Special chars: * means all
  default attributes, "-" excludes an attribute. Note that sub-attributes of complex attributes are not fully supported.
* `name` - Name is a descriptive label for an ACI. The name may be descriptive such as describing the ACI's purpose.
* `rights` - The permissions granted or allowed (all, add, modify, delete, read, search, compare)
    * `all` - all of the rights below
    * `add` - The ability to create a resource using HTTP POST
    * `modify` - The ability to modify a resource using HTTP PUT or PATCH
    * `delete` - The ability to delete a resource using HTTP DELETE
    * `read` - The ability to return attributes from any operation.
    * `search` - The ability to use a search filter using HTTP GET or POST.
* `actors` - A MVA whos values are the entities the rule applies to.The actor need only match one value condition.
  Values are: any, self, role, filter, ref where:
  * `any` - any subject (including anonymous if enabled)
  * `self` - means the authenticated subject and the resoruce acted upon are the same (e.g. using the /Me endpoint)
  * `role` - means the authenticated subject holds the specified role:
    * for Bearer authentication asserted in JWT `scopes` claim(see property `scim.security.authen.jwt.claim.scope`), or 
    * for basic authentication via SCIM internal asserted by the SCIM `roles` attribute. 
    
      Notes: By default, all internal users are assigned the role `user`. Bearer authenticated clients are assigned the role "bearer".
      A subject or user matching Connfig property `scim.security.root.username` is assigned the role of `root`.
  * `ref` - means the subject matches the reference (internal or external URI)
  * `filter` - means the authenticated subject matches the supplied SCIM filter (is an internal user). May not be
    supportable for federated clients.

###### Notes on rights:
* `search` entitles the client to apply a filter against certain attribute and return an `id`. In all cases, the attributes
returned are determined by read entitlement. 
* `compare` (from LDAP models) is not supported because SCIM protocol (RFC7644) does not support a true/false type of error response.
* For SCIM `Create`, `Put`, or `Patch`, the `read` right controls what attributes may be returned. For example, if
an actor has `add` and/or `put` rights but not `read`, then only common attributes: `id` and `schemas` attributes are returned in the result confirming
the operation success when SCIM returns a JSON result.
  
###ACI Evaluation Processing:

1. When multiple ACIs apply to a SCIM entity, acis are applied in order of longest path first. The operation proceeds
  on the first aci that permits the operation. The attributes permitted in the operation are the merged `targetAttrs` list
   from all matched ACIs.
2. An `aci` specified at a certain path applies to the path and any potential child nodes. For example "/" shall apply
  to the entire server where as "/Users" applies to all user resources, and "/Users/63c02009-3cdd-4b56-89f3-3a56e68bc80d" applies
  to a specific resource.
3. If `targetfilter` and `targetattrs` specified, both must be satisfied
4. For `read`, `create`, `modify` (PUT), `targetAttr` clauses silently limits what may be changed or added and the operation
   may proceed without the unauthorized attributes providing the operation's required attributes requirements are met. A 
   `modify` in the form of a SCIM Patch will return 'Unauthorized' if the modified attribute is not allowed.
5. `targetFilter` processing:  For a SCIM Create, targetFilter is applied to the new resource to be created. 
  For all other operations, the filter is applied to the existing resource before the operation. When no objects 
  match the targetFilter, a NotFound error is thrown.
6. If a SCIM filter "search" must be allowed or 'Unauthorized' is
  returned.

The server reads its ACIs configuration from a JSON file specified by the system property: `scim.security.acis.path`
### Examples:

<PRE>
"acis": [
  {
    "path" : "/Users",
    "name" : "Self and employee access to read information",
    "targetAttrs" : "*,-password",
    "rights" : "read, search, compare",
    "actors" : ["self", "filter=employeeNumber pr"]
  },
  
  {
    "path" : "/",
    "name" : "Administrators can read, search, compare all records",
    "targetAttrs" : "*",
    "rights" : "read, search, compare",
    "actors" : [
        "group=/Groups/e9e30dba-f08f-4109-8486-d5c6a331660a"
        "role=admin"
    ]
  },
  
  {
    "name" : "Allow unauthenticated access to names and email addresses of Users",
    "targetFilter":"meta.resourceType eq User",
    "targetAttrs" :"username,displayName,emails,name,phoneNumbers",
    "rights" : "read, search, compare",
    "actors" : ["any"]
  }
]
</PRE>