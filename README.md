# question-answering
A simple Solr package for a question answering engine

## Installing

* Start Solr (version 8.4 or later) nodes with -Denable.packages=true

    `bin/solr -c -Denable.packages=true`

* Add repository:

    `bin/solr package add-repo chatman-qa "https://raw.githubusercontent.com/chatman/question-answering/master/repo/"`

* See available packages:

    `bin/solr package list-available`

* Install the package

    `bin/solr package install question-answering`

* Create a collection and add a document

    `curl "http://localhost:8983/solr/admin/collections?action=CREATE&name=facts&numShards=1" && curl -XPOST -d '[{"id": 1, "name_t":"Japan", "type_s": "country", "capital_s": "Tokyo"}]' "http://localhost:8983/solr/facts/update?commit=true"`

* Deploy package on the collection

    `bin/solr package deploy question-answering -y -collections facts -p RH-HANDLER-PATH=/qa`

* Use the plugin

    `curl "http://localhost:8983/solr/facts/qa?q=what%20is%20the%20capital%20of%20japan"`

## Development

You need to follow the prerequisites one time, and release process everytime you wish to release a new package.

### Prerequisites

* Create your private key/public key pair to sign your packages:

    `openssl genrsa -out privatekey.pem 512 && openssl rsa -in privatekey.pem -pubout -outform DER -out publickey.der`

* Keep your `privatekey.pem` securely and outside of your Git repository. This will be used to sign all future releases of your packages.

* Place your publickey.der file in the `repo/` folder.

* Please note that if a repository has already been added in Solr, but the public key changes (as it would if you follow the above steps), you would need to add the repository again (maybe with some other name than that was previously added, `chatman-qa` in this case).

### Releasing

* Increase the version in `pom.xml`. Adjust the `src/resources/manifest.json`, if needed.

* Do `mvn package`, which will place the new jar in `repo` directory.

* Sign the jar with your private key:

    `openssl dgst -sha1 -sign <path-to-privatekey.pem> <new-jar-file> | openssl enc -base64`

* Add a section in `repo/repository.json` file with the release details. The signature will be same as output from previous command (but concatenate the multiline output into a single string, without any whitespace).

* Add, commit, push your changes to the `repo/` folder. Test your release by `bin/solr package list-available` on a Solr instance where this repository has been added already.
