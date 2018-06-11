import tensorflow as tf
print("-------")
hello = tf.constant('Hello, TensorFlow!')
sess = tf.Session()

print()
print(sess.run(hello))