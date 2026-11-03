def specificationFile = new File(basedir, 'docs/openapi.json')
assert specificationFile.isFile() : "no ${specificationFile}"
def specification = new groovy.json.JsonSlurper().parse(specificationFile)
assert specification.info.title == 'Greetings'
assert specification.paths.containsKey('/greetings/{name}')
assert specification.components.schemas.GreetingDto.properties.text.type == 'string'
assert !specification.containsKey('servers')
