# glacierpipe
===========

glacierpipe is a command line tool for piping data from ```stdin``` to an archive on Amazon Glacier, generally as part of a
backup process where storing the file on disk prior to uploading is undesirable. 

Its arguments are compatible with [MoriTanosuke's glacieruploader](https://github.com/MoriTanosuke/glacieruploader/).

##Typical use

```
tar -c <directory> | \
xz | \
java ... glacierpipe.GlacierPipeMain \
    --upload \
    --endpoint us-east-1 \
    --partsize 33554432 \
    --vault <vault name> \
    <archive name>.tar.xz
```

e.g.

```
$ tar -c /home | xz | java -jar glacierpipe.jar \
    --upload \
    -e us-east-1 \
    -p 8388608 \
    -v home-backups \
    home.tar.xz
Upload ID: BuRHlPMY-Vfj37rf0LLUrR9cel9xun6WrwNNrLsoyOFJPGT0Nz3VBWXhLCxpMlO...
Part 0, 0 B - ?
  Buffering...
  [ <=>                           ] 8.000 MB 12.491 MB/s in 00.64s

  Tree Hash: 0x70e3ac4b6bddf0823520515daf573a64cb70781c2ad189a3879c797cdb9e75e8

  Uploading...
  100% [==============================>] 8.000 MB 0.277 MB/s in 28.92s

Part 1, 8.000 MB - ?
  Buffering...
  [ <=>                           ] 8.000 MB 10.860 MB/s in 00.74s

  Tree Hash: 0x6f3dd7d9b4e27d681be9e5b1ca4275402095e1d3bf1702f653902373dc14ff4d

  Uploading...
   32% [==========>                    ] 2.598 MB 0.332 MB/s eta 16.44s
```

##Building
```
$ ant
```

This will create ```./dist/glacierpipe.jar```.

##Internals
Entire parts are read from ```stdin```, buffered in memory, and a SHA-256 tree hash computed on them prior to
upload.  In the event a part fails to upload, since the part was buffered in memory, uploading just that part is
reattempted.