## CLI commands for endpoints

### Greetings

```bash
http POST http://localhost:9000/greetings/123 message=hi
```


### Activity agent
```bash
curl -i -XPOST --location "http://localhost:9000/activities" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do?"}'
```
