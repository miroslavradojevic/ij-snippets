y_test <- data.matrix(read.table(file.path("C:\\Users\\10250153\\bacteria3\\experiment1", "y_test.txt"), header=F, sep=" "))
y_pred <- data.matrix(read.table(file.path("C:\\Users\\10250153\\bacteria3\\experiment1", "y_pred.txt"), header=F, sep=" "))

tp = length(which(y_test==1 & y_pred==1))
fp = length(which(y_test==0 & y_pred==1))
fn = length(which(y_test==1 & y_pred==0))

p = tp/(tp+fp)
r = tp/(tp+fn)
f = (2*p*r)/(p+r)