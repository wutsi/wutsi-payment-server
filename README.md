[![](https://github.com/wutsi/wutsi-payment-server/actions/workflows/master.yml/badge.svg)](https://github.com/wutsi/wutsi-payment-server/actions/workflows/master.yml)

[![JDK](https://img.shields.io/badge/jdk-11-brightgreen.svg)](https://jdk.java.net/11/)
[![](https://img.shields.io/badge/maven-3.6-brightgreen.svg)](https://maven.apache.org/download.cgi)
![](https://img.shields.io/badge/language-kotlin-blue.svg)

API for managing payments.&#10;This API allows to&#10;- Cash in: Move money from owner&#39;s account into the
wallet&#10;- Cash out: Move monet from wallet to owner&#39;s account&#10;- Transfer: Transfer money between
wallets&#10;- Search transactions&#10;

# Installation Prerequisites

## Database Setup

- Install postgres
- Create account with username/password: `postgres`/`postgres`
- Create a database named `wutsi-payment`

## Configure Github

- Generate a Github token for accessing packages from GibHub
    - Goto [https://github.com/settings/tokens](https://github.com/settings/tokens)
    - Click on `Generate New Token`
    - Give a value to your token
    - Select the permissions `read:packages`
    - Generate the token
- Set your GitHub environment variables on your machine:
    - `GITHUB_TOKEN = your-token-value`
    - `GITHUB_USER = your-github-user-name`

## Maven Setup

- Download Instance [Maven 3.6+](https://maven.apache.org/download.cgi)
- Add into `~/m2/settings.xml`

```
    <settings>
        ...
        <servers>
            ...
            <server>
              <id>github</id>
              <username>${env.GITHUB_USER}</username>
              <password>${env.GITHUB_TOKEN}</password>
            </server>
        </servers>
    </settings>
```

## Usage

- Install

```
$ git clone git@github.com:wutsi/wutsi-payment-server.git
```

- Build

```
$ cd wutsi-payment-server
$ mvn clean install
```

- Launch the API

```
$ mvn spring-boot:run
```

That's it... the API is up and running! Start sending requests :-)

# Links

- [Event](docs/Event.md)
- Payment Operations
    - [Cashin](docs/Cashin.md)
    - [Cashout](docs/Cashout.md)
    - [Transfer](docs/Transfer.md)
- [API](https://wutsi.github.io/wutsi-payment-server/api/)

