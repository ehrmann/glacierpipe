glacierpipe
===========

glacierpipe is a command line tool for piping data from stdin to an archive on Amazon Glacier.
Its arguments are compatible with [MoriTanosuke's glacieruploader](https://github.com/MoriTanosuke/glacieruploader/).

Typical use:

```
tar -c <directory> | xz | java ... glacierpipe.GlacierPipeMain --upload --endpoint us-east-1 --partsize 33554432 --vault <vault name> <archive name>
```
