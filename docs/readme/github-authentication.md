---
description: Get read access to the project and be able to pull the images
---

# GitHub Authentication

#### Authenticate with the GitHub container registry (ghcr.io)

In order to be able to view the code and pull the images, there are a few steps to take.

1. Request to join the [`pace-alpha-testers`](https://github.com/orgs/getstrm/teams/pace-alpha-testers) team.
2. Create a new Personal Access Token (PAT) through your [GitHub settings page](https://github.com/settings/tokens/new). You only need to specify the **read:packages** scope.
3. If you are approved to join the team your can execute the following command:  `docker login ghcr.io -u <your-github-username>` . When prompted, enter the PAT you just created.

You are now ready to install PACE.
