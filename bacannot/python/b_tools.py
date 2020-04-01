import os
import itertools
import random as rnd
import numpy as np
import matplotlib.pyplot as plt


def load_data(data_dir, img_ext, annot_dirs):
    ftifs = [os.path.join(data_dir, f)
             for f in os.listdir(data_dir)
             if f.endswith("." + img_ext)]

    flist = []
    for ftif in ftifs:
        ftif_dir = os.path.abspath(os.path.join(ftif, os.pardir))
        ftif_name = os.path.basename(ftif)
        flist.append((ftif,))
        for annot_dir in annot_dirs:
            flist[-1] = flist[-1] + (os.path.join(ftif_dir, annot_dir, ftif_name),)
    return flist


def systematic_resampling(weights, mask, number_samples):
    cws = np.zeros(len(weights))

    for i in range(len(cws)):
        if mask is None:
            cws[i] = weights[i] + (0.0 if (i == 0) else cws[i - 1])
        else:
            cws[i] = (weights[i] if (mask[i] == 255) else 0.0) + (0.0 if (i == 0) else cws[i - 1])

    out = np.zeros(number_samples).astype(int)

    totalmass = cws[len(cws) - 1]

    # systematic re-sampling
    i = int(0)

    u1 = (totalmass / float(number_samples)) * rnd.uniform(0, 1)

    for j in range(number_samples):
        uj = u1 + j * (totalmass / float(number_samples))
        while uj > cws[i]:
            i += 1
        out[j] = i
    return out


def grid_sampling(mask, step):
    rows = []
    cols = []
    for r in range(0, mask.shape[0], step):
        for c in range(0, mask.shape[1], step):
            if mask[r, c] > 0:
                rows.append(r)
                cols.append(c)

    return rows, cols


def updatecat(category_name, category_index, map):
    if category_name not in map:
        category_index += 1
        map[category_name] = category_index
    else:
        category_index = map[category_name]

    print(category_index, " -> ", map)

    return category_index, map


def get_locs(n_rows, n_cols, step, D_rows, D_cols, circ_radius_ratio):

    rows = []
    cols = []

    for row in range(0, n_rows, step):
        for col in range(0, n_cols, step):
            row0 = int(row - D_rows / 2)
            row1 = int(row + D_rows / 2)
            col0 = int(col - D_cols / 2)
            col1 = int(col + D_cols / 2)
            if row0 >= 0 and row1 < n_rows and col0 >= 0 and col1 < n_cols:
                if circ_radius_ratio is not None:
                    if pow(row - n_rows / 2, 2) + pow(col - n_cols / 2, 2) <= pow(circ_radius_ratio * min(n_rows / 2, n_cols / 2), 2):
                        rows.append(row)
                        cols.append(col)
                else:
                    rows.append(row)
                    cols.append(col)

    return rows, cols


def get_locs_random(n_rows, n_cols, D_rows, D_cols, circ_radius_ratio, nr_samples):

    smap = np.ndarray(shape=(n_rows, n_cols), dtype='uint8') * 0

    for row_smap in range(0, n_rows):
        for col_smap in range(0, n_cols):
            row_smap_min = int(row_smap - D_rows / 2)
            row_smap_max = int(row_smap + D_cols / 2)
            col_smap_min = int(col_smap - D_rows / 2)
            col_smap_max = int(col_smap + D_cols / 2)
            if row_smap_min >= 0 and row_smap_max < n_rows and col_smap_min >= 0 and col_smap_max < n_cols:
                if circ_radius_ratio is not None:
                    if pow(row_smap - n_rows / 2, 2) + pow(col_smap - n_cols / 2, 2) <= pow(circ_radius_ratio * min(n_rows / 2, n_cols / 2), 2):
                        smap[row_smap, col_smap] = 255
                else:
                    smap[row_smap, col_smap] = 255

    # use smap[] as weight

    smap = smap.reshape(np.prod(smap.shape))
    smap = smap / 255.0

    for i in range(0, np.prod(smap.shape)):
        smap[i] *= rnd.random()

    smap_locs = systematic_resampling(smap, None, nr_samples)

    rows = []
    cols = []

    for i in range(0, len(smap_locs)):
        row = smap_locs[i] // n_cols
        col = smap_locs[i] % n_cols

        rows.append(row)
        cols.append(col)

    return rows, cols


def plot_confusion_matrix(cm, classes,
                          normalize=False,
                          title='Confusion matrix',
                          cmap=plt.cm.Blues):
    """
    This function prints and plots the confusion matrix.
    Normalization can be applied by setting `normalize=True`.
    """
    if normalize:
        cm = cm.astype('float') / cm.sum(axis=1)[:, np.newaxis]
        print("Normalized confusion matrix")
    else:
        print('Confusion matrix, without normalization')

    print(cm)

    plt.imshow(cm, interpolation='nearest', cmap=cmap)
    plt.title(title)
    plt.colorbar()
    tick_marks = np.arange(len(classes))
    plt.xticks(tick_marks, classes, rotation=45)
    plt.yticks(tick_marks, classes)

    fmt = '.2f' if normalize else 'd'
    thresh = cm.max() / 2.
    for i, j in itertools.product(range(cm.shape[0]), range(cm.shape[1])):
        plt.text(j, i, format(cm[i, j], fmt),
                 horizontalalignment="center",
                 color="white" if cm[i, j] > thresh else "black")

    plt.ylabel('True label')
    plt.xlabel('Predicted label')
    plt.tight_layout()



