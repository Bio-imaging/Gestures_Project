import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import csv

epoch = [11]
data = pd.read_csv('./test_data/mbientlab_poke_right.csv',header = None)

for i in range(1,41):
    print ("data length : {}".format(len(data)))
    data_x = data.iloc[i,0:149]   # 0 150
    data_y = data.iloc[i,150:299] # 150 300
    data_z = data.iloc[i,300:449] # 300 450
    data_xx = data.iloc[i,450:599]   # 0 150
    data_yy = data.iloc[i,600:749] # 150 300
    data_zz = data.iloc[i,750:899] # 300 450

    #print(len(data_x))
    #print(len(data_y))
    #print(len(data_z))
    #print(data_x)

    xf = np.linspace(0,2,len(data_x))
    plt.figure(i)
    ax0 = plt.subplot(211)
    ax0.plot(xf,data_x,'b')
    #ax1 = plt.subplot(312)
    ax0.plot(xf,data_y,'r')
    #ax2 = plt.subplot(313)
    ax0.plot(xf,data_z,'y')

    ax1 = plt.subplot(212)
    ax1.plot(xf,data_xx,'b')
    #ax1 = plt.subplot(312)
    ax1.plot(xf,data_yy,'r')
    #ax2 = plt.subplot(313)
    ax1.plot(xf,data_zz,'y')
plt.show()

print("data shape : {}".format(data.shape))
"""
csvdata = []
f = open('./test_data/test_1.csv', 'w')
wr = csv.writer(f)
wr.writerow(data)
f.close()
"""
