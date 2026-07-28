# j0-code

Modulo dedicato alla generazione di solo codice Java tramite provider LLM gia' presenti in `j0-react`, senza loop ReAct e senza tool.

## Obiettivo

- il provider deve rispondere con il solo sorgente Java
- il modulo salva il file `.java`
- compila il sorgente con il JDK corrente
- esegue la classe generata e stampa l'output

## Esempio

```bash
mvn -pl j0-code package
java -jar j0-code/target/j0-code.jar --userprompt "stampa Hello from j0-code" --provider llamacpp
```