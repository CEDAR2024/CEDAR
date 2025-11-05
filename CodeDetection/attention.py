import torch
from torch import nn
import torch.nn.functional as F


def _default_device() -> torch.device:
    if torch.backends.cuda.is_built():
        try:
            if torch.cuda.is_available():
                return torch.device("cuda")
        except (AssertionError, RuntimeError):
            pass
    return torch.device("cpu")


class SelfAttentionHead(nn.Module):
    def __init__(self, input_dim, head_size):
        super().__init__()
        device = _default_device()
        self.key = nn.Linear(input_dim, head_size, bias=False)
        self.query = nn.Linear(input_dim, head_size, bias=False)
        self.value = nn.Linear(input_dim, head_size, bias=False)
        self.scale = torch.sqrt(torch.FloatTensor([head_size])).to(device)

    def forward(self, x):
        if x.dim() == 2:
            x = x.unsqueeze(1)  #  [batch_size, feature_dimension] -> [batch_size, 1, feature_dimension]
        k = self.key(x)
        q = self.query(x)
        v = self.value(x)
        attention = torch.bmm(q, k.transpose(1, 2))
        attention = attention / self.scale
        attention = F.softmax(attention, dim=-1)
        x = torch.bmm(attention, v)
        return x

class MultiHeadSelfAttention(nn.Module):
    def __init__(self, input_dim, num_heads, head_size):
        super().__init__()
        self.heads = nn.ModuleList(
            [SelfAttentionHead(input_dim, head_size) for _ in range(num_heads)]
        )
        self.linear = nn.Linear(num_heads * head_size, input_dim)

    def forward(self, x):
        if x.dim() == 2:
            x = x.unsqueeze(1)  #  [batch_size, feature_dimension] -> [batch_size, 1, feature_dimension]
        x = [head(x) for head in self.heads]
        x = torch.cat(x, dim=2)
        x = self.linear(x)
        return x
