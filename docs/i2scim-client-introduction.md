# i2scim Client Library

## Introduction

`i2scimClient` is Java library that enables developers to work with [SCIMv2](https://simplecloud.info) service providers
to provision, update, and manage SCIM resources such as Users and Groups or other custom resource types.

The `i2scimClient` has the following features:

* Maven ready
* Auto-configuring - automatically interrogates a SCIM service provider to discover capabilities, schema,
  and resource types. The client then throws local errors where possible to report schema violations or unimplemented
  features (e.g. no support for filters, or PATCH requests).
* Dynamic Schema - i2scim project does not have fixed POJO objects. Instead `i2scimClient` uses `ScimResource` objects 
  which are able to handle any SCIM compliant JSON document. Schema definitions can be auto-loaded from a SCIM 
  service provider or loaded from JSON files locally.
* Supports the Java [Builder pattern](https://en.wikipedia.org/wiki/Builder_pattern#Java).
* Parses SCIM responses into a universal `i2scimResponse` object with SCIM Java Exceptions.
* Uses a streaming parser to enable processing of very large results in a small memory footprint
* Uses Jackson JSON parser and Apache HTTP Client library.

## 1. Overview

`i2scimClient` functionality centers around the `i2scimClient` class which provides HTTP client connectivity as well as
access to builders and SCIM protocol methods. When executing a SCIM operation, the HTTP responses are parsed and returned as
a `i2scimResponse` object. Use `i2scimClient` to access the `ResourceBuilder` which may be used to manipulate resources 
locally and update back to the SCIM service provider associated with the `i2scimClient` that created the builder.

## 2. Setting Up Your Maven Project

Add the following dependencies to your `pom.xml`:

```xml

<dependencies>
    <dependency>
        <groupId>com.independentid</groupId>
        <artifactId>i2scim-core</artifactId>
        <version>0.7.0-Alpha</version>
    </dependency>
    <dependency>
        <groupId>com.independentid</groupId>
        <artifactId>i2scim-client</artifactId>
        <version>0.7.0-Alpha</version>
    </dependency>
</dependencies>
```

## 3. Setting up a connection

The i2scimClient is initialized by invoiking one of the constructors. The following modes are available:
* i2scimClient(String serverUrl, String authorization) - use this method to connect to a server at a specified URL 
  and an value to be passed as the HTTP Authorization header.
* i2scimClient(String serverUrl, UsernamePasswordCredentials cred) - initialize a connection using basic 
  authorization using the Apache `UsernamePasswordCredentials`.
* i2scimClient(SchemaManager schemaManager) initializes a local instance based on an existing SchemaManager instance.
  This constructor is only useful for accessing builder functions and manipulating ScimResource objects locally.

Note that in most use cases in identity management, a SCIM client credential is usually privileged in the sense that 
it has some level of access to search, retrieve, and/or update objects. Usually the client is initialized with the 
administrative credential in order to perform multiple requests.

The following code sets up a client connection based on a username and password.
```java
import com.independentid.scim.client.ResourceBuilder;
import com.independentid.scim.client.ScimReqParams;
import com.independentid.scim.client.i2scimClient;
import com.independentid.scim.client.i2scimResponse;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.schema.SchemaManager;
import org.apache.http.auth.UsernamePasswordCredentials;

public class MyClient() {
    public void initializeClient(String id, String pwd, String url) {

        UsernamePasswordCredentials cred = new UsernamePasswordCredentials(id, pwd);
        try {
            i2scimClient client = new i2scimClient(url, cred);

            SchemaManager schemaManager = client.getSchemaManager();

            Schema userSchema = schemaManager.getSchemaByName("User");

            Attribute name = schemaManager.findAttribute("User:name", null);

        } catch (ScimException e) {
            // Occurs when a defined SCIM Protocol or Schema error occurs
        } catch (IOException e) {
            // occurs when a basic HTTP IO error has occurred.
        }
    }
}
```

The above code fragment shows the establishment of an `i2scimClient` instance. The constructor performs the following:

1. Attempts to connect to the URL provided and adds the path `/ServiceProviderConfig` to discover the server's
   capabilities. The requests uses the credential provided for this an all future requests. This constructor uses Basic
   auth, but constructors support any HTTP Authorization header required. B. The response is procesed and capabilities
   of the server can be interrogated later by using `i2scimClient`
   methods such as:

    * `hasSearchSupport()`
    * `hasNoEtagSupport()`
    * `hasSortSupport()`
    * `hasChangePasswordSupport()`
    * `hasPatchSupport()`

2. The client constructor then makes calls to the `/Schemas` and `/ResourceTypes` service provider endpoints to 
   download the schemas and
   resource types available. The returned schema can be obtained by using the `getSchemaManager()` method of
   `i2scimClient`. The SchemaManager class can then be used to look up resource types, schema, and attribute
   definitions.<BR><BR>

   Following return of the client instance, the example code above obtains the `SchemaManager` instance to look 
   up the `User` `Schema` object. It also obtains the `name` `Attribute` as defined in the `User` `Schema`. Note that 
   when using `findAttribute`, the path
   provided is an attribute path. This can include an optional full Schema URI or Schema name, a colon ':' and an
   attribute name and an optional sub-attribute. Valid examples are:
    * `User:name`
    * `urn:ietf:params:scim:schemas:core:2.0:User:name.familyName`
    * `userName`

   In the first example, the schema name prefixes the attribute name using a colon. In the second example, the
   sub-attribute of name, `familiyName` is returned. This example uses the full Schema id. The third example skips the
   schema name and searches all schemas for the attribute named `userName`. Note that the attribute `name` appears in
   multiple schemas and you may not get the Attribute expected if you find just `name`. It is a good practice to
   include the schema name or id to distinguish attributes such as `User:name`.

<br><br>
**_Note: in all following examples, the variable `client` is assumed to be a client created using an `i2scimClient`
constructor._**

## 4. Using the ResourceBuilder

### Building a ScimResource from Scratch

This section demonstrates:
* how to use a builder to build a ScimResource from scratch,
* adding simple attribute values,
* adding complex, multi-attribute values,
* adding multi-valued attribute values, and,
* adding data as a JSON object in String form.

The following block of code shows the construction of a ScimResoure object with an assigned set of values using a
builder. The client utilities also provide ways to load and parse resources from files or JSON strings directly as well.

In the code below, the #s correspond to the numbered comments in the code

1. The i2scimClient `client` instance is used to return a `ResourceBuilder` object. A simple string attribute value is
   added using the `.addStringAttribute` method. In the method, the attribute name (`username`) and value (`jim123`)
   are provided.
2. A `ComplexValue` can be added by using `addComplexAttribute` together with `client.getComplexValueBuilder`. In this
   case, the `User:name` attribute has several sub-attributes to be defined in order to build the JSON structure. At the
   end of the ComplexValue construction, the `buildComplexValue()` method is called.
3. The third exmaple, shows adding a multi-valued attribute that has ComplexValues attached. In this case `emails`. A
   work and a home value are added. Note that as an alternative, the MultiValueBuilder does not have to be used.
   Repeated calls to addMultiValueAttribute with a singular Value or ComplexValue may also be made. When this happens
   the builder simply adds the new values to the existing values.
4. As with 3, a multi-valued list is created for `addresses`. The `withJsonString()` method is used to parse a JSON
   String directly as a `Value`.
5. Finally, the `.build()` method is called to construct and return the `ScimResource`.

```java
    public ScimResource buildMyResource(){

        String addressJson ="""
                {"streetAddress": "456 Hollywood Blvd",
                "locality": "Hollywood",
                "region": "CA",
                "postalCode": "91608",
                "country": "USA",
                "formatted": "456 Hollywood Blvd\\nHollywood, CA 91608 USA",
                "type": "home"}""";

        ResourceBuilder builder = client.getResourceBuilder("Users")
            .addStringAttribute("username","jim123")                      // <-- #1

            .addComplexAttribute(client.getComplexValueBuilder("User:name")
                .withStringAttribute("familyName","Smith")
                .withStringAttribute("givenName","Jim")
                .withStringAttribute("middleName","John")
                .withStringAttribute("honorificSuffix","Jr")
                .buildComplexValue())                                  // <--- #2

            .addStringAttribute("displayName","Jim Smith")
            .addStringAttribute("nickName","Jim")

            .addMultiValueAttribute(client.getMultiValueBuilder("emails")  // <--- #3
                .withComplexValue(client.getComplexValueBuilder("emails")
                    .withStringAttribute("type","work")
                    .withStringAttribute("value","jsmith@example.com")
                    .withBooleanAttribute("primary",true)
                    .buildComplexValue())
                .withComplexValue(client.getComplexValueBuilder("emails")
                    .withStringAttribute("type","home")
                    .withStringAttribute("value","jimmy@nowhere.com")
                    .buildComplexValue())
                .buildMultiValue())

            .addMultiValueAttribute(client.getMultiValueBuilder("addresses")
                .withComplexValue(client.getComplexValueBuilder("addresses")
                    .withStringAttribute("streetAddress","100 W Broadway Ave")
                    .withStringAttribute("locality","Vancouver")
                    .withStringAttribute("region","BC")
                    .withStringAttribute("country","CA")
                    .withStringAttribute("type","work")
                    .buildComplexValue())
                .withJsonString(addressJson)                        // <--- #4
                .buildMultiValue());

            return builder.build();                                 // <--- #5
        }
```

### Using Builder to Load Existing Resource Data

i2scimClient provides four other methods to initialize a `ResourceBuilder`:
* `i2scimClient.getResourceBuilder(JsonNode json)` - initializes a builder passed a `JsonNode` pointing to SCIM resource 
  object.
* `i2scimClient.getResourceBuilder(ScimResource resource)` - initializes a builder by copying an existing `ScimResource`.
* `i2scimClient.getResourceBuilder(File file)` - initializes a builder by parsing a file that contains a JSON 
  representation of a SCIM resource.
* `i2scimClient.getResourceBuilder(InputStream stream)` - initializes a builder by parsing JSON data from a stream.

### Modifying Resource Using Builder

Once the builder is returned, the add<type> (e.g.`addStringAttribute`) methods can be used to change the resource before 
building. If a value is multi-valued, the new value is added to the existing values. If the value is single-valued, 
the new value replaces the existing value if any.

To remove an values for an attribute (or sub-attribute), use the `removeAttribute(Attribute attr)` method. If the 
attribute is multi-valued, all values are removed.  

## 5. Creating A Resource

The `i2scimClient` library provides two methods to create a resource at the SCIM Provider. Method 1, is to use the
builder directly. The other method, uses the `i2scimClient`. In general, if you use the builder to perform a request,
the result returned is the final `ScimResource` returned. When using `i2scimClient`, the full i2scimResponse object is 
returned.

### Builder Create Method

Instead of constructing the resource and passing it to the client, the `ResourceBuilder.buildAndCreate()` method 
can be 
calleed.

When using the builder, the result returned is the parsed `ScimResoure` returned by the service provider. In order to
detect errors, catch the appropriate ScimException. Typical errors returned might involve a missing required attribute
or forbidden exception if the authorization used to establish the client connection has insufficient access rights.

```java
        ResourceBuilder builder=client.getResourceBuilder("Users")
            .addStringAttribute("username","jim123")

            .addComplexAttribute(client.getComplexValueBuilder("User:name")
                .withStringAttribute("familyName","Smith")
                .withStringAttribute("givenName","Jim")
                .withStringAttribute("middleName","John")
                .withStringAttribute("honorificSuffix","Jr")
                .buildComplexValue())

            .addStringAttribute("displayName","Jim Smith")
            .addStringAttribute("nickName","Jim");

        ScimResource createdResource=builder.buildAndCreate(null);
```

### Client Create Method

The `i2scimClient` can be used to create a new resource directly by passing the `ScimResource` to be created. A
`i2scimResponse` object is returned by the create method that contains the parsed results or any errors. The returned
ScimResource representation can be retrieved by calling the `i2scimResponse#next()` method.

In order to detect errors, the `hasError()` method is called. the `getException()`, and `getStatus()` methods may be
used to detect what error occurred.

```java
    public ScimResource createResource(ScimResource resource){}
        i2ScimResponse response = client.create(resource,null);
        if (!response.hasError() && response.hasNext())
            return response.next();
        return null;
    }
```

## 6. Retrieving Resources

Given a URL for a SCIM resource, the resource may be returned using the `i2scimClient#get()` method.

In order to detect errors, the `hasError()` method is called. the `getException()`, and `getStatus()` methods may be
used to detect what error occurred.

```java
    public ScimResource getResource(String url)throws ScimException{
        i2ScimResponse response = client.get(url,null);
        if (!response.hasError())
            if(response.hasNext())
                return response.next(); // <- this contains the parse ScimResource
            else
                return null;
        throw response.getException();
    }
```

The following example shows retrieving a resource only if it has been modified since a particular date.

```java
    ScimReqParams params = new ScimReqParams();
    params.setHead_ifModSince(modificationDate);
    
    i2ScimResponse response = client.get(url,params);
```

## 7. Querying Resources

SCIM supports searching via both a `HTTP GET` and `HTTP POST`. The `HTTP POST` method is provided to avoid conveying 
confidential information within a request URL which may later turn up in an access log. The `i2scimClient` offers 
`searchGet` and `searchPost` methods which are functionally equivalent however the latter
uses `HTTP POST` to perform the request and passes request parameters in the HTTP payload rather then the request URL.

In the following example, `searchPost` is used and the example code builds an array of `ScimResource` objects using 
the i2scimResponse object as an `Iterator`. 

Note: that if a result set is very large, memory could be exceeded when loading all results into an array. 
Because of this, many SCIM servers will limit results to a specified limit (e.g. 1000) unless the client access authorization allows it to exceed the limits.

```java
    public List<ScimResource> searchResource(String filter)throws ScimException{
        i2scimResponse response = client.searchPost(url,filter,null);
        if (!response.hasError())
            if (response.hasNext()) {
                ArrayList<ScimResource> items = new ArrayList<>();
                resp.forEachRemaining(items::add);
                return items; // <- this contains the parse ScimResource
            } else
                return null;
        throw response.getException();
    }
```

Note: When a SCIM Service Provider provides a SCIM ListResponse formatted result, the order of attributes matters to 
the i2scimClient. If the `totalResults` attribute is written before the `Resources` array, the `getTotalResults()`
method will return the correct value. When `getTotalResults()` returns a value of -1, the streaming parser was not 
able to parse the result count before parsing resources due to the order of output in the response.

## 8. Other Operations

This section covers PUT, PATCH, DELETE, and HEAD request.

### SCIM PUT Request

SCIM PUT is an easy way for a client to update a SCIM Resource. Often, clients will retrieve a SCIM Resource, change 
a field, and want to send it back for modification without having to do a lot of complex manipulation - in other 
words, to put it back. Note: that for some service providers SCIM PUT may seem simpler, but it often requires more 
internally complex locking procedures.

A SCIM PUT request works in similar fashion to the create request except it follows the SCIM processing rules:

* The resource in the PUT request MUST have a pre-existing id value that corresponds to the request URL.
* Attributes that are immutable or read-only are ignored
* The request may be rejected if the client is not authorized to update the resource or specific attributes.

Two Examples:

* `ScimResource res = builder.buildAndPut(params);` - In this case the `ResourceBuilder` can be used to directly 
  manipulate and then update a previously returned ScimResource. The final representation is returned or `null`.
* `i2scimResponse response = client.put(resource,params)` - In this case, a `ScimResource` is passed with an optional
  `ScimReqParams` value.

### SCIM PATCH Request

A SCIM PATCH operation is performed using the `JsonPatchBuilder` and then submitting the request using
the `i2scimClient.patch` method. SCIM Patch requests are based on JSONPatch [RFC6902](https://datatracker.ietf.org/doc/html/rfc6902)
but with some minor changes to support non-indexed addressing of arrays. PATCH is useful when manipulating very 
large resources such as groups with 1000s or millions of member values.

The `i2scimClient` provides a `JsonPachRequestBuilder` which enables construction of a JSON Patch request compliant with 
SCIM. In the following example, removes a User's social media attribute `ims` of the specified by parameter `type` and adds a
a new value specified by parameter `newHandle`.

```java
    public ScimResource patchResourceIms(String userUrl, String type, String newHandle) throws ScimException {
        JsonPatchRequest req = client.getPatchRequestBuilder()
                .withRemoveOperation("ims[type eq " + type + "]")
                .withAddOperation("ims",
                        client.getComplexValueBuilder("ims")
                                .withStringAttribute("type", type)
                                .withStringAttribute("value", newHandle)
                                .buildComplexValue())
                .build();
        i2scimResponse resp = client.patch(userUrl, req, null);
        resp.close();
        if (!response.hasError())
            if (resp.hasNext())
                return response.next();
            else
                return null;
        throw response.getException();
    }
```

### Delete Request

A SCIM Delete request consists of a specific resource URL to be deleted plus any request pre-conditions specified
by `ScimReqParams`. An `i2scimResponse` is returned that will indicate success or failure.

For example:

```java
   i2scimResponse resp = client.delete(userUrl,params);
```

### Head Request

`HTTP HEAD` requests are useful to confirm the continued existence of a resource and to obtain a resource's 
`lastModified` date. Note that while HEAD is indirectly specified with RFC7232 and pre-condition requests in the 
[Versioning section of RFC7644](https://datatracker.ietf.org/doc/html/rfc7644#section-3.14), many SCIM service 
providers may not implement this method.

```java
    i2scimResponse resp = client.headInfo(resourceUrl,null);

    if (resp.getStatus().equals(HttpStatus.SC_OK)) {
        String modDate = resp.getLastModification();
        System,out.println("Found. Last modified on "+modDate);
    } else if (resp.getStatus().equals(HttpStatus.SC_NOT_FOUND))
        System.out.println("Not Found");
    ...
```

## 8. Authentication

*Experimental*

The `i2scimClient.authenticateUser` method is use to confirm a username & password is a match. This feature works by 
converting the supplied credential into a filter and uses the searchPost method to check for a match. Note that this 
does not establish a session or alter the underlying credential used by the i2scimClient. This method is provided to 
assist a service component (e.g. an OpenID Connect Provider) in the process of authenticating a user only.

Note also that some SCIM service provider implementations do not support password comparisons over filter.

## 9. Scim Request Parameters

The ScimReqParams class allows SCIM Protocol request parameters to be passed to the i2scimClient. Use this class
to specify:
* HTTP request PRECONDITIONS per [RFC7232](https://datatracker.ietf.org/doc/html/rfc7232)
* [Attributes](https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.2.5) to be returned or excluded
* [Sorting parameters](https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.2) including `SortBy` and `SortOrder`
* [Paging request parameters](https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.2.4) including startIndex and count.

When the i2scim client formats a request, it will create the appropriate HTTP headers, URL parameters or payload
parameters when searching using `searchPost`.

## 10. Notes on streaming parser

The `i2scimClient` class implements a streaming JSON parser. Since SCIM does not require a specific order or 
attributes in a SCIM response, there are times in a ListResponse, where the `totalResults` attribute may occur after 
the `Resources` attribute. In such cases, `i2scimResponse.getTotalResults()` returns `-1` while iterating over the 
result set.