import pandas as pd, numpy as np, joblib
from sklearn.neighbors import KNeighborsClassifier, NearestNeighbors

d = pd.read_csv("train.csv", names=["ts", "label", "bssid", "rssi"])
X = d.pivot_table(index=["label", "ts"], columns="bssid", values="rssi").fillna(-100)
y = X.index.get_level_values("label")

model = KNeighborsClassifier(n_neighbors=5, weights="distance").fit(X.values, y)

# ngưỡng "lạ": nếu một scan cách mọi mẫu đã học quá xa thì không đoán bừa
dist = NearestNeighbors(n_neighbors=2).fit(X.values).kneighbors(X.values)[0][:, 1]
thr = float(np.percentile(dist, 99) * 1.5)

joblib.dump({"model": model, "bssids": list(X.columns), "thr": thr}, "model.joblib")
print("Các nhãn:", list(model.classes_), "| ngưỡng lạ:", round(thr, 1))