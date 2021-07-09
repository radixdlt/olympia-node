extern crate bytebuffer;

use bytebuffer::ByteBuffer;
use std::fmt;

use crate::substates::*;
use crate::types::*;

pub struct Transaction {
    pub instructions: Vec<Instruction>,
}

#[derive(Debug)]
pub enum Instruction {
    END,

    SYSCALL(Bytes),

    UP(Box<dyn Substate>),

    READ(SubstateId),

    LREAD(SubstateIndex),

    VREAD(VirtualSubstateID),

    LVREAD(LocalVirtualSubstateID),

    DOWN(SubstateId),

    LDOWN(SubstateIndex),

    VDOWN(VirtualSubstateID),

    LVDOWN(LocalVirtualSubstateID),

    SIG(Signature),

    MSG(Bytes),

    HEADER(u8, u8),

    READINDEX(Bytes),

    DOWNINDEX(Bytes),
}

impl Transaction {
    pub fn from_bytes(bytes: Vec<u8>) -> Self {
        let mut instructions = Vec::new();
        let mut buffer = ByteBuffer::from_bytes(&bytes[..]);
        while buffer.get_rpos() < buffer.get_wpos() {
            let inst = Instruction::from_buffer(&mut buffer);
            // println!("{:?}", inst);
            instructions.push(inst);
        }
        Self { instructions }
    }
}

impl fmt::Debug for Transaction {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Instructions:\n").unwrap();
        self.instructions
            .iter()
            .for_each(|i| write!(f, "|- {:?}\n", i).unwrap());
        fmt::Result::Ok(())
    }
}

impl Instruction {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let t = buffer.read_u8();
        match t {
            0x00 => Self::END,
            0x01 => Self::SYSCALL(Bytes::from_buffer(buffer)),
            0x02 => Self::UP(Self::read_substate(buffer)),
            0x03 => Self::READ(SubstateId::from_buffer(buffer)),
            0x04 => Self::LREAD(buffer.read_u16()),
            0x05 => Self::VREAD(VirtualSubstateID::from_buffer(buffer)),
            0x06 => Self::LVREAD(LocalVirtualSubstateID::from_buffer(buffer)),
            0x07 => Self::DOWN(SubstateId::from_buffer(buffer)),
            0x08 => Self::LDOWN(buffer.read_u16()),
            0x09 => Self::VDOWN(VirtualSubstateID::from_buffer(buffer)),
            0x0A => Self::LVDOWN(LocalVirtualSubstateID::from_buffer(buffer)),
            0x0B => Self::SIG(Signature::from_buffer(buffer)),
            0x0C => Self::MSG(Bytes::from_buffer(buffer)),
            0x0D => Self::HEADER(buffer.read_u8(), buffer.read_u8()),
            0x0E => Self::READINDEX(Bytes::from_buffer(buffer)),
            0x0F => Self::DOWNINDEX(Bytes::from_buffer(buffer)),
            _ => panic!("Unexpected opcode: {:#04X}", t),
        }
    }

    fn read_substate(buffer: &mut ByteBuffer) -> Box<dyn Substate> {
        let _size = buffer.read_u16();
        let t = buffer.read_u8();
        match t {
            0x04 => Box::new(TokenResource::from_buffer(buffer)),
            0x05 => Box::new(TokenResourceMetadata::from_buffer(buffer)),
            0x06 => Box::new(Tokens::from_buffer(buffer)),
            0x07 => Box::new(PreparedStake::from_buffer(buffer)),
            0x08 => Box::new(StakeOwnership::from_buffer(buffer)),
            0x09 => Box::new(PreparedUnstake::from_buffer(buffer)),
            0x0A => Box::new(ExitingStake::from_buffer(buffer)),
            0x0E => Box::new(ValidatorAllowDelegationFlag::from_buffer(buffer)),
            0x0F => Box::new(ValidatorRegisteredFlagCopy::from_buffer(buffer)),
            0x10 => Box::new(ValidatorRakeCopy::from_buffer(buffer)),
            0x11 => Box::new(ValidatorOwnerCopy::from_buffer(buffer)),
            _ => panic!("Unsupported substate type: {:#04X}", t),
        }
    }
}

#[cfg(test)]
mod tests {
    use crate::transaction::Transaction;
    use std::fs;

    #[test]
    fn token_create() {
        let contents = fs::read_to_string("../samples/token_create.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn token_mint() {
        let contents = fs::read_to_string("../samples/token_mint.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn token_transfer() {
        let contents = fs::read_to_string("../samples/token_transfer.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn token_burn() {
        let contents = fs::read_to_string("../samples/token_burn.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn xrd_transfer() {
        let contents = fs::read_to_string("../samples/xrd_transfer.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn xrd_transfer_with_msg() {
        let contents = fs::read_to_string("../samples/xrd_transfer_with_msg.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn xrd_stake() {
        let contents = fs::read_to_string("../samples/xrd_stake.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn xrd_unstake() {
        for n in 1..3 {
            let contents = fs::read_to_string(format!("../samples/xrd_unstake{}.txt", n)).unwrap();
            let raw = hex::decode(contents).unwrap();
            let tx = Transaction::from_bytes(raw);
            println!("{:?}", tx)
        }
    }

    #[test]
    fn validator_register() {
        let contents = fs::read_to_string("../samples/validator_register.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn validator_unregister() {
        let contents = fs::read_to_string("../samples/validator_unregister.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn validator_re_register() {
        let contents = fs::read_to_string("../samples/validator_re_register.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn validator_allow_delegation() {
        let contents = fs::read_to_string("../samples/validator_allow_delegation.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn other_transfer_to_self() {
        let contents = fs::read_to_string("../samples/other_transfer_to_self.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn other_transfer_mixed_tokens() {
        let contents = fs::read_to_string("../samples/other_transfer_mixed_tokens.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn other_stake_from_validator_1() {
        let contents = fs::read_to_string("../samples/other_stake_from_validator_1.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn other_stake_from_validator_2() {
        let contents = fs::read_to_string("../samples/other_stake_from_validator_2.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn other_complex_fee() {
        let contents = fs::read_to_string("../samples/other_complex_fee.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }
}
