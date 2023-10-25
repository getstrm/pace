import yaml
with open('../protos/gen/openapi.yaml', 'r') as file:
    spec = yaml.safe_load(file)
APIReference = '\n\n'.join(["# API Reference"] +
    [ 
        f'''{{% swagger src=".gitbook/assets/openapi.yaml" path="{path}" method="{method}" expanded="true" %}}\n[openapi.yaml](.gitbook/assets/openapi.yaml)\n{{% endswagger %}}''' 
        for path in spec['paths']
        for method in spec['paths'][path].keys()
    ]
)
with open('README.md', 'w') as file:
    file.write(APIReference)
