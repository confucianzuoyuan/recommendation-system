imagefolderlist = []

with open('imagefolderlist.txt') as f:
    for line in f.readlines():
        imagefolderlist = line.split(',')
        break

print(len(imagefolderlist))

newdata_f = open('newdata.txt', 'w')


with open('tags.csv') as f:
    for line in f.readlines():
        if line.split(',')[1] in imagefolderlist:
            newdata_f.write(line)
