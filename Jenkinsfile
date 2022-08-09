// Specify configurations explicitly to test against newer LTS.
// See also https://github.com/jenkins-infra/pipeline-library/pull/145

buildPlugin(useContainerAgent: true, configurations: [
  [ platform: 'linux', jdk: '8' ],
  [ platform: 'linux', jdk: '11' ],
  [ platform: 'windows', jdk: '11' ],
])
