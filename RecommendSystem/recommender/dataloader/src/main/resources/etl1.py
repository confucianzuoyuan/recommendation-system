imagelist = []
with open('movies.csv') as f:
    for line in f.readlines():
        imagelist.append(line.split('^')[0])

with open('imagelist.txt', 'w') as f:
    f.write(','.join(imagelist))
