# gRPC API Reference

To build PACE, we use gRPC and Protobuf. This allows us to develop API first, and we use [buf.build](https://buf.build)
to do so.

The API definitions can be viewed in either [GitHub](https://github.com/getstrm/pace/tree/alpha/protos/getstrm/pace/api)
or on the [Buf Schema Registry](https://buf.build/getstrm/pace).

## Invoking RPCs

To interact with the PACE gRPC API, you can use one of these tools. Both use server reflection, which allows for
discovering the available RPCs:

- [Postman](https://learning.postman.com/docs/sending-requests/grpc/grpc-request-interface/): graphical user interface
- [Evans](https://github.com/ktr0731/evans/): command line interface

At getSTRM, we use both tools, depending on the use case.
