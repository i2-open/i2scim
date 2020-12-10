# Access Control
## *To Be Implemented*

This SCIM server uses a configuration filter access.aci that specifies access control policy for the server.

This ACI format follows a format inspired by LDAP access control systems.

aci - An Access Control Instruction where:
   
Has the following sub-attributes:
* path - The server path where the ACI is applied. "/" is global, whereas "/Users" applies to the Users endpoint. An ACI may also be applied to an individual resource (e.g. /users/resource-identifier).
* targetfilter - A SCIM filter specifying a set of resources
* targetattrs - A comma separated String containing the attriibutes applied to the aci. Special chars: * means all default attributes, "-" excludes an attribute, 
* Name - Name is a descriptive label for an ACI. The name may be descriptive such as describing the ACI's purpose.
* Rights - The permissions granted (all, add, modify, delete, read, search, compare)
    * all - all of the rights below
    * add - The ability to create a resource using HTTP POST
    * modify - The ability to modify a resource using HTTP PUT or PATCH
    * delete - The ability to delete a resource using HTTP DELETE
    * read - The ability to return a resource using HTTP GET
    * search - The ability to return resources using a search filter using HTTP GET or POST
    * compare - The ability to apply a filter and return an id for a resource
  
* Actor - A MVA whos values are the entities the rule applies to.The actor need only match one value condition. Values are: any, self, role, filter, ref where:
    * any - any subject (including anonymous if enabled)
    * self - means the authenticated subject and the resoruce acted upon are the same
    * role - means the authenticated subject holds the specified role
    * ref - means the subject matches the reference (internal or external URI)
    * filter - means the authenticated subject matches the supplied SCIM filter (is an internal user). May not be supportable for federated clients.

Examples:
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
    "name" : "Administrators can read, search, compare all records and operational attributes",
    "targetAttrs" : "*,+",
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