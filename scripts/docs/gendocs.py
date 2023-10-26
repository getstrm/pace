import yaml
with open('../../protos/gen/openapi.yaml', 'r') as file:
    spec = yaml.safe_load(file)
APIReference = '\n\n'.join(["# API Reference"] +
    [
        f'''{{% swagger src=".gitbook/assets/openapi.yaml" path="{path}" method="{method}" summary="{spec['paths'][path][method]['operationId'].replace(spec['paths'][path][method]['tags'][0] + "_", "")}" %}}
        [openapi.yaml](.gitbook/assets/openapi.yaml)
        {{% swagger-description %}}

        {{% endswagger-description %}}
        {{% endswagger %}}'''
        for path in spec['paths']
        for method in spec['paths'][path].keys()
    ]
)
with open('../../docs/README.md', 'w') as file:
    file.write(APIReference)
