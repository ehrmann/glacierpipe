# glacierpipe
===========

glacierpipe is a command line tool for piping data from stdin to an archive on Amazon Glacier.
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
$ tar -c /home | xz | java -jar glacierpipe.jar --upload -e us-east-1 -p 8388608 -v home-backups home.tar.xz
Upload ID: BuRHlPMY-Vfj37rf0LLUrR9cel9xun6WrwNNrLsoyOFJPGT0Nz3VBWXhLCxpMlO5903NmfEsB0aT0kyc1ZRXnwr03TV_
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
  100% [==============================>] 8.000 MB 0.278 MB/s in 28.73s

Part 2, 16.000 MB - ?
  Buffering...
  [ <=>                           ] 8.000 MB 10.315 MB/s in 00.78s

  Tree Hash: 0x47d4f8de85961a74ded2fc85a5257af5318ef9f7007a5adc80d2e17ea3a9a434

  Uploading...
  100% [==============================>] 8.000 MB 0.279 MB/s in 28.64s

Part 3, 24.000 MB - ?
  Buffering...
  [ <=>                           ] 8.000 MB 9.539 MB/s in 00.84s

  Tree Hash: 0xaa14871849bd8a71d4ac23ee780a28945f82a48cef33062f5266d82754b2446a

  Uploading...
   32% [==========>                    ] 2.598 MB 0.332 MB/s eta 16.44s
```
