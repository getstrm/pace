import yaml
with open('../../protos/gen/openapi.yaml', 'r') as file:
    spec = yaml.safe_load(file)
    ref = ["# API Reference"]
    for path in spec['paths']:
        for method in spec['paths'][path].keys():
            spec['paths'][path][method]['summary'] = spec['paths'][path][method]['operationId'].replace(spec['paths'][path][method]['tags'][0] + "_", "")
            ref.append(f'''{{% swagger src=".gitbook/assets/openapi.yaml" path="{path}" method="{method}" expanded="true" %}}\n[openapi.yaml](.gitbook/assets/openapi.yaml)\n{{% endswagger %}}''')

with open('../../docs/.gitbook/assets/openapi.yaml', 'w') as file:
    yaml.dump(spec, file, default_flow_style=False)

with open('../../docs/README.md', 'w') as file:
    file.write('\n\n'.join(ref))
