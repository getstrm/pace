# [1.0.0-alpha.23](https://github.com/getstrm/pace/compare/v1.0.0-alpha.22...v1.0.0-alpha.23) (2023-11-17)


### Bug Fixes

* **pace-37:** improve regexp transforms and add transformer tests ([#60](https://github.com/getstrm/pace/issues/60)) ([6533005](https://github.com/getstrm/pace/commit/653300514e0496e5505c40599fe0bb2b3232fc57))


### Features

* **feature/pace-5:** added displayName for Collibra databases ([b71110e](https://github.com/getstrm/pace/commit/b71110ee4cf0c29f5df8c72fc1e9f5c1d7687f68))

# [1.0.0-alpha.22](https://github.com/getstrm/pace/compare/v1.0.0-alpha.21...v1.0.0-alpha.22) (2023-11-16)


### Bug Fixes

* **feature/pace-32:** handles absent description ([c8ce92e](https://github.com/getstrm/pace/commit/c8ce92eb37b924a15fe4486334ca22ea37aecb06))


### Features

* **feature/pace-32:** handles validation errors in blueprint policy ([30a0bc9](https://github.com/getstrm/pace/commit/30a0bc948aee57f754407c41d76a5e24fe7cd0fd))

# [1.0.0-alpha.21](https://github.com/getstrm/pace/compare/v1.0.0-alpha.20...v1.0.0-alpha.21) (2023-11-15)


### Features

* **pace-19:** add retention to rulesets for filtering rows ([#50](https://github.com/getstrm/pace/issues/50)) ([6e77039](https://github.com/getstrm/pace/commit/6e770396dc41e743adf01a6e22a4aec464276dfe))

# [1.0.0-alpha.20](https://github.com/getstrm/pace/compare/v1.0.0-alpha.19...v1.0.0-alpha.20) (2023-11-15)


### Features

* **feature/pace-31:** added loose tag matching ([f6d6a2b](https://github.com/getstrm/pace/commit/f6d6a2b7068e059ce3abdad798bce9ff2be6bec1))
* **feature/pace-31:** case insensitive global transform tags ([53d1e0d](https://github.com/getstrm/pace/commit/53d1e0db0989e280e7db32c2925e97c066d09b8f))
* **feature/pace-31:** merged origin/alpha ([2116e39](https://github.com/getstrm/pace/commit/2116e397971bac1fbd8544f79c4ff4546c6a036f))
* **feature/pace-31:** removed RefAndType, and ref in GlobalTransform ([c0f0526](https://github.com/getstrm/pace/commit/c0f05269a058267638610be026255739e5659257))

# [1.0.0-alpha.19](https://github.com/getstrm/pace/compare/v1.0.0-alpha.18...v1.0.0-alpha.19) (2023-11-15)


### Features

* **pace-28:** add numeric rounding transform ([#38](https://github.com/getstrm/pace/issues/38)) ([693ebe5](https://github.com/getstrm/pace/commit/693ebe5a4ff56961fac3ecb052b09b2d3092e965))

# [1.0.0-alpha.18](https://github.com/getstrm/pace/compare/v1.0.0-alpha.17...v1.0.0-alpha.18) (2023-11-15)


### Features

* **pace-13:** use dollar signs to reference capturing groups in regexp transforms ([2a484f4](https://github.com/getstrm/pace/commit/2a484f45ea1bbc6f319c3c3be256540dec898581))

# [1.0.0-alpha.17](https://github.com/getstrm/pace/compare/v1.0.0-alpha.16...v1.0.0-alpha.17) (2023-11-14)


### Bug Fixes

* **pace-17-4:** cel validation for list global transforms ([#44](https://github.com/getstrm/pace/issues/44)) ([2df9d8d](https://github.com/getstrm/pace/commit/2df9d8dda6ba74814538a0a89710b2eacdd096df))

# [1.0.0-alpha.16](https://github.com/getstrm/pace/compare/v1.0.0-alpha.15...v1.0.0-alpha.16) (2023-11-14)


### Features

* **pace-17-3:** add http paths for global transforms ([#43](https://github.com/getstrm/pace/issues/43)) ([16e5898](https://github.com/getstrm/pace/commit/16e58987a6c2fe5e81fbbd74f4fd6e109ed37b7a))

# [1.0.0-alpha.15](https://github.com/getstrm/pace/compare/v1.0.0-alpha.14...v1.0.0-alpha.15) (2023-11-13)


### Bug Fixes

* generate protos ([82f895f](https://github.com/getstrm/pace/commit/82f895f0b8b5c6207c5a16a1fdb77bfb0245de91))
* remove duplicate openapi spec ([2ef1e64](https://github.com/getstrm/pace/commit/2ef1e644505df1fa2480ec21c8dd64fb370b92bd))

# [1.0.0-alpha.14](https://github.com/getstrm/pace/compare/v1.0.0-alpha.13...v1.0.0-alpha.14) (2023-11-13)


### Features

* **pace-17-2:** add validation to check whether GlobalTransform.RefA… ([#39](https://github.com/getstrm/pace/issues/39)) ([9c1aeaa](https://github.com/getstrm/pace/commit/9c1aeaad3d26270851b632d7efb4e7096b861af6))

# [1.0.0-alpha.13](https://github.com/getstrm/pace/compare/v1.0.0-alpha.12...v1.0.0-alpha.13) (2023-11-10)


### Features

* **pace-17:** Global Tag Transforms ([#36](https://github.com/getstrm/pace/issues/36)) ([a95b9f5](https://github.com/getstrm/pace/commit/a95b9f562041ecaa0d293e037d1fd67605a6cb07))

# [1.0.0-alpha.12](https://github.com/getstrm/pace/compare/v1.0.0-alpha.11...v1.0.0-alpha.12) (2023-11-09)


### Features

* **pace-24:** implement basic detokonization transform ([#35](https://github.com/getstrm/pace/issues/35)) ([1bdc088](https://github.com/getstrm/pace/commit/1bdc0889eb5600e57307fed03979b5cf821a6d6f))

# [1.0.0-alpha.11](https://github.com/getstrm/pace/compare/v1.0.0-alpha.10...v1.0.0-alpha.11) (2023-11-07)


### Bug Fixes

* ensure release succeeds with replacing the docker compose image tag ([e6f1dea](https://github.com/getstrm/pace/commit/e6f1dea689d01e0cad389325c9fe954941925ff0))


### Features

* add postgres processing platform support + standalone example ([6e80b94](https://github.com/getstrm/pace/commit/6e80b940b29580aac8efd61f946431b1c8e27fd9))

# [1.0.0-alpha.10](https://github.com/getstrm/pace/compare/v1.0.0-alpha.9...v1.0.0-alpha.10) (2023-11-03)


### Bug Fixes

* **alpha:** run build before semantic release & fix snowflake converter test ([43c6ac9](https://github.com/getstrm/pace/commit/43c6ac91dca946bf7e1297899c29fdc838a5da1a))

# [1.0.0-alpha.9](https://github.com/getstrm/pace/compare/v1.0.0-alpha.8...v1.0.0-alpha.9) (2023-11-02)


### Bug Fixes

* **alpha:** added normalizeType to processing platforms code ([4fe6f21](https://github.com/getstrm/pace/commit/4fe6f214bedd642873282fc98633ab535325906c))

# [1.0.0-alpha.8](https://github.com/getstrm/pace/compare/v1.0.0-alpha.7...v1.0.0-alpha.8) (2023-11-02)


### Bug Fixes

* **alpha:** trigger docs build ([42210f7](https://github.com/getstrm/pace/commit/42210f74e80ad418f7158fd790aba7d243702b8e))


### Features

* **strm-2725:** change snowflake secret configuration ([0afcc60](https://github.com/getstrm/pace/commit/0afcc6082087cb202d237f20c6354bf90969deea))

# [1.0.0-alpha.7](https://github.com/getstrm/pace/compare/v1.0.0-alpha.6...v1.0.0-alpha.7) (2023-11-02)


### Bug Fixes

* correct directory to openapi spec for docs ([b6b12ba](https://github.com/getstrm/pace/commit/b6b12ba8df06d5b886850b7a4fa5b8bcbf10f935))

# [1.0.0-alpha.6](https://github.com/getstrm/pace/compare/v1.0.0-alpha.5...v1.0.0-alpha.6) (2023-11-02)


### Bug Fixes

* minor comment change in protos; trigger docs release ([7eeb466](https://github.com/getstrm/pace/commit/7eeb466cd527084abee154e36caebdaa3fef9f8e))

# [1.0.0-alpha.5](https://github.com/getstrm/pace/compare/v1.0.0-alpha.4...v1.0.0-alpha.5) (2023-11-02)


### Features

* **alpha:** cli-docs ([10ee7cc](https://github.com/getstrm/pace/commit/10ee7cca094df57faf0947cdff8478d4eb0bfa2b))
* **strm-2724:** various api and DAO improvements ([#12](https://github.com/getstrm/pace/issues/12)) ([7a30074](https://github.com/getstrm/pace/commit/7a300741165d18301f00fe3741d84c8a1ab904bf))

# [1.0.0-alpha.4](https://github.com/getstrm/pace/compare/v1.0.0-alpha.3...v1.0.0-alpha.4) (2023-11-01)


### Bug Fixes

* ensure that descriptor.binpb is created after gradle build ([d5dc32d](https://github.com/getstrm/pace/commit/d5dc32da59f3dd3a5dbeadd33187e1194ba8c27c))

# [1.0.0-alpha.3](https://github.com/getstrm/pace/compare/v1.0.0-alpha.2...v1.0.0-alpha.3) (2023-11-01)


### Bug Fixes

* remove unwanted spring.config.import ([9f33dfb](https://github.com/getstrm/pace/commit/9f33dfb0a8e79c77b17ee7e6d6ec0c459699e761))
* remove unwanted spring.config.import ([48e6e2a](https://github.com/getstrm/pace/commit/48e6e2aba672b0c298ed2786be6c0a2193b661e2))

# [1.0.0-alpha.2](https://github.com/getstrm/pace/compare/v1.0.0-alpha.1...v1.0.0-alpha.2) (2023-11-01)


### Features

* add extra /app/config directory for providing SB config ([d2ca87b](https://github.com/getstrm/pace/commit/d2ca87bc138eb9862281de69c9c2dc052768aaf4))

# 1.0.0-alpha.1 (2023-11-01)


### Features

* initial alpha commit ([5978ed3](https://github.com/getstrm/pace/commit/5978ed315cd6c5fc2bef2480cd9a0b4f71be320c))
