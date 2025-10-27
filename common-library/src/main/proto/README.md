# Protocol Buffers (Proto) Definitions

This directory contains all `.proto` files for gRPC service definitions.

## Structure

```
proto/
├── cart.proto          # Cart Service gRPC definitions (Week 1, Day 15)
├── order.proto         # Order Service gRPC definitions (Week 4, Day 17)
├── payment.proto       # Payment Service gRPC definitions (Week 4, Day 18)
├── inventory.proto     # Inventory Service gRPC definitions (Week 4, Day 19)
└── common.proto        # Common message types
```

## Usage

Proto files are automatically compiled by the Maven `protobuf-maven-plugin` during the build process.

Generated Java classes will be placed in:
- `target/generated-sources/protobuf/java/` - Message classes
- `target/generated-sources/protobuf/grpc-java/` - Service stubs

## Compilation

To compile proto files:
```bash
mvn clean compile
```

## Proto File Standards

1. Use `proto3` syntax
2. Include package declaration: `package com.ecommerce.grpc;`
3. Set Java package option: `option java_package = "com.ecommerce.grpc";`
4. Set Java multiple files option: `option java_multiple_files = true;`
5. Use clear, descriptive message and service names
6. Document all messages and fields with comments

## Example Proto File

```protobuf
syntax = "proto3";

package com.ecommerce.grpc;

option java_package = "com.ecommerce.grpc";
option java_multiple_files = true;

// Service definition
service ExampleService {
  rpc DoSomething(ExampleRequest) returns (ExampleResponse);
}

// Request message
message ExampleRequest {
  string id = 1;
}

// Response message
message ExampleResponse {
  string result = 1;
}
```

## Notes

- Proto definitions will be added according to the development schedule
- Services using gRPC: Cart, Order, Payment, Inventory
- All other services use REST APIs
