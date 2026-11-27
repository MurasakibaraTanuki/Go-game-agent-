from .policy import probabilities, sample

def main():
    base = [0.5, 0.3, 0.2, -1.0]
    residual = [-0.3, 0.8, 0.1, 100.0]
    for w in (0, 0.5, 1):
        p = probabilities(base, residual, w)
        print(f'weight={w}: {[round(x, 4) for x in p]} sampled={sample(base, residual, weight=w, seed=7)}')
    print('Synthetic example only; no trained master or calibrated rank.')

if __name__ == '__main__':
    main()
